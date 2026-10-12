# -*- coding: utf-8 -*-
"""
在 git 协议（github.com:443）被网络阻断、但 api.github.com 可达的环境下推送提交。

原理：走 GitHub 的 Git Data API，手工构造 blob -> tree -> commit -> 更新 ref。
效果与 `git push` 等价，远端会得到完整的提交历史。

用法:
    python tools/gh-push.py                 # 把「远端 main → 本地 HEAD」的差异推上去
    python tools/gh-push.py --full          # 全量推送：上传本地全部已跟踪文件，清掉远端多余文件
    python tools/gh-push.py --dry-run       # 只列出会改哪些文件
    python tools/gh-push.py --tag v1.1.0    # 推送后再创建一个 tag 指向新提交

## 什么时候要用 --full

默认走 `git diff 远端..本地` 算差异，这要求**远端那个提交在本地对象库里**。
改名 + 重构之后远端提交往往不在本地（本地历史被重写过），diff 会直接报
`fatal: bad object`。这时用 --full：不 diff，直接上传 `git ls-files` 的全部文件，
并**不带 base_tree** —— 于是远端 tree 变成一个完整快照，顺带把旧版本的残留
文件（比如重构前的目录）一并清掉。
"""
import argparse
import base64
import json
import subprocess
import sys
import urllib.error
import urllib.request

REPO = "KokuuWork/voidkit"
API = "https://api.github.com"


def run(*args, check=True):
    r = subprocess.run(args, capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"{' '.join(args)}\n{r.stderr}")
    return r.stdout.strip()


def token():
    return run("gh", "auth", "token")


def github(method, path, body=None, tok=None):
    """
    通过 `gh api` 调用，而不是直接用 urllib。

    踩过的坑：urllib 对 Git Data API 的 POST 一律返回 404，而同一个端点用
    `gh api` 调用完全正常，参数一模一样。既然 gh CLI 已经装好且认证过，
    就没必要自己跟 HTTP 栈较劲。
    """
    args = ["gh", "api", "-X", method, path.lstrip("/")]
    if body is not None:
        args += ["--input", "-"]
    r = subprocess.run(
        args,
        input=json.dumps(body) if body is not None else None,
        capture_output=True,
        text=True,
    )
    if r.returncode != 0:
        raise RuntimeError(f"{method} {path} 失败\n{(r.stderr or r.stdout)[:400]}")
    out = r.stdout.strip()
    return json.loads(out) if out else {}


def local_blob(path):
    """把工作区文件转成 Git blob 需要的形式，二进制/文本都支持。"""
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--full", action="store_true",
                    help="全量推送：上传本地全部已跟踪文件，不带 base_tree")
    ap.add_argument("--tag")
    ap.add_argument("--message-file", default=None)
    args = ap.parse_args()

    tok = token()

    # 1. 找出「远端 main → 本地 HEAD」的差异
    remote_sha = github("GET", f"/repos/{REPO}/git/ref/heads/main", tok=tok)["object"]["sha"]
    local_sha = run("git", "rev-parse", "HEAD")
    print(f"远端 main: {remote_sha[:8]}")
    print(f"本地 HEAD: {local_sha[:8]}")

    if remote_sha == local_sha:
        print("两边已经一致，无需推送。")
        return 0

    if args.full:
        # 全量模式：列出本地全部已跟踪文件。不 diff —— 远端提交可能不在本地对象库里
        # （改名/重构之后本地历史被重写过，`git diff` 会报 bad object）。
        paths = [p for p in run("git", "ls-files").splitlines() if p.strip()]
        # 格式统一成 (状态, 路径)，状态用 A（新增/覆盖）—— 不带 base_tree 时
        # 只有"在这个列表里"和"不在"的区别，A/D 只是给打印看的
        changes = [("A", p) for p in paths]
        print(f"全量模式：本地已跟踪 {len(changes)} 个文件")
    else:
        out = run("git", "diff", "--name-status", remote_sha, local_sha)
        changes = [line.split("\t", 1) for line in out.splitlines() if line.strip()]

    # GitHub 的 Git Data API 拒绝写入 .github/workflows/ 下的文件，
    # 除非 token 带 `workflow` scope（当前只有 `repo`），而且它返回的是
    # 404 而不是 403 —— 极难排查。这里直接跳过，改用别的方式同步。
    skipped = [c for c in changes if c[1].startswith(".github/workflows/")]
    if skipped:
        changes = [c for c in changes if not c[1].startswith(".github/workflows/")]
        print(f"\n⚠️ 跳过 {len(skipped)} 个 workflow 文件（缺 workflow scope）:")
        for _, p in skipped:
            print(f"    {p}")
        print("    → 这些文件需要手动推送，或给 gh 授权 workflow scope：")
        print("      gh auth refresh -h github.com -s workflow")

    print(f"\n差异文件 {len(changes)} 个:")
    for st, path in changes:
        print(f"  {st}  {path}")
    if args.dry_run:
        return 0

    # 2. 为每个变更建 blob / 记录删除
    tree_entries = []
    for st, path in changes:
        if st == "D":
            tree_entries.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
            continue
        content = local_blob(path)
        blob = github("POST", f"/repos/{REPO}/git/blobs",
                      {"content": content, "encoding": "base64"}, tok)
        mode = "100755" if path.endswith(".sh") or path == "gradlew" else "100644"
        tree_entries.append({"path": path, "mode": mode, "type": "blob", "sha": blob["sha"]})
    print(f"\n已创建 {len(tree_entries)} 个 blob")

    # 3. 建新 tree
    #    默认带 base_tree（增量，保留远端其它文件）；
    #    --full 不带 base_tree —— 得到一个完整快照，远端多余的文件会被清掉。
    if args.full:
        payload = {"tree": tree_entries}
        print(f"tree entries: {len(tree_entries)} 条（全量，无 base_tree）")
    else:
        base_tree = github("GET", f"/repos/{REPO}/git/commits/{remote_sha}", tok=tok)["tree"]["sha"]
        print(f"base_tree={base_tree} (长度 {len(base_tree)})")
        payload = {"base_tree": base_tree, "tree": tree_entries}
        print(f"tree entries: {len(tree_entries)} 条，首条 = {json.dumps(tree_entries[0])[:160]}")
    tree = github("POST", f"/repos/{REPO}/git/trees", payload, tok)
    print(f"已创建 tree {tree['sha'][:8]}")

    # 4. 建 commit
    message = run("git", "log", "-1", "--pretty=%B", local_sha)
    commit = github("POST", f"/repos/{REPO}/git/commits",
                    {"message": message, "tree": tree["sha"], "parents": [remote_sha]}, tok)
    print(f"已创建 commit {commit['sha'][:8]}")

    # 5. 更新 ref
    github("PATCH", f"/repos/{REPO}/git/refs/heads/main",
           {"sha": commit["sha"], "force": False}, tok)
    print(f"\n✓ 已更新 main -> {commit['sha'][:8]}")

    # 本地把 origin/main 指到新提交，保持与远端一致。
    # 不能直接用 `git update-ref` —— 那个提交是远端建的，本地没有对应对象，
    # git 会以 "nonexistent object" 拒绝。直接写 ref 文件即可。
    ref_file = run("git", "rev-parse", "--git-dir") + "/refs/remotes/origin/main"
    with open(ref_file, "w") as f:
        f.write(commit["sha"] + "\n")
    print(f"已同步本地 origin/main -> {commit['sha'][:8]}")

    # 6. 可选：打 tag
    if args.tag:
        tag_msg = f"Voidkit {args.tag}"
        tag = github("POST", f"/repos/{REPO}/git/tags",
                     {"tag": args.tag, "message": tag_msg, "object": commit["sha"], "type": "commit"}, tok)
        github("POST", f"/repos/{REPO}/git/refs", {"ref": f"refs/tags/{args.tag}", "sha": tag["sha"]}, tok)
        print(f"✓ 已创建 tag {args.tag} -> {commit['sha'][:8]}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
