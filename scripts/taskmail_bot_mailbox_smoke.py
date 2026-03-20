from __future__ import annotations

import argparse
import imaplib
import json
import secrets
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Any

SCRIPT_PATH = Path(__file__).resolve()
ANDROID_REPO_ROOT = SCRIPT_PATH.parent.parent
MAIL_WORKSPACE_ROOT = ANDROID_REPO_ROOT.parent / "mail_based_task_manager"

sys.path.insert(0, str(MAIL_WORKSPACE_ROOT))

from mail_runner.config import AppConfig, load_config  # noqa: E402
from mail_runner.mail_io import (  # noqa: E402
    SYSTEM_MESSAGE_HEADER,
    SYSTEM_MESSAGE_HEADER_VALUE,
    MailClient,
    message_bytes_to_envelope,
)

DEFAULT_BOT_CONFIG = MAIL_WORKSPACE_ROOT / "mail_config.bot.local.yaml"
DEFAULT_USER_CONFIG = MAIL_WORKSPACE_ROOT / "mail_config.local.yaml"
DEFAULT_OUTPUT_DIR = ANDROID_REPO_ROOT / "_tmp_taskmail_bot_mailbox_smoke"


def _timestamp_slug() -> str:
    return datetime.now().strftime("%Y%m%d_%H%M%S")


def _normalize(value: str) -> str:
    return value.strip().lower()


def _write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def _write_text(path: Path, payload: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(payload, encoding="utf-8")


def _envelope_to_dict(envelope) -> dict[str, Any]:
    return {
        "message_id": envelope.message_id,
        "subject": envelope.subject,
        "from_addr": envelope.from_addr,
        "to_addr": envelope.to_addr,
        "date": str(envelope.date),
        "in_reply_to": envelope.in_reply_to,
        "references": list(envelope.references),
        "body_text": envelope.body_text,
        "raw_headers": dict(envelope.raw_headers),
    }


def _scan_recent_messages(config: AppConfig, *, scan_limit: int) -> list[dict[str, Any]]:
    client = imaplib.IMAP4_SSL(config.imap_host, config.imap_port)
    messages: list[dict[str, Any]] = []
    try:
        client.login(config.imap_user, config.imap_password)
        status, _ = client.select("INBOX", readonly=True)
        if status != "OK":
            raise RuntimeError("Unable to select INBOX.")
        status, data = client.search(None, "ALL")
        if status != "OK":
            raise RuntimeError("Unable to search mailbox.")

        mail_ids = data[0].split()
        if scan_limit > 0:
            mail_ids = mail_ids[-scan_limit:]

        for raw_id in mail_ids:
            status, payload = client.fetch(raw_id, "(BODY.PEEK[])")
            if status != "OK" or not payload or not payload[0]:
                continue
            message_bytes = payload[0][1]
            envelope = message_bytes_to_envelope(
                message_bytes,
                raw_id.decode("ascii", errors="ignore"),
            )
            messages.append(
                {
                    "imap_id": raw_id.decode("ascii", errors="ignore"),
                    "envelope": envelope,
                }
            )
    finally:
        try:
            client.logout()
        except Exception:
            pass

    return messages


def _build_seed_payload(
    *,
    session_id: str,
    thread_id: str,
    workspace_id: str,
    session_name: str,
    task_id: str,
    reply_token: str,
) -> dict[str, str]:
    summary = (
        "这是 TaskMail Android bot mailbox 冒烟测试。"
        f"请在手机里打开 Tasks，进入此任务后回复精确文本 {reply_token} 并发送。"
        "不要点击 /status，不要改动大小写。"
    )
    body = "\n".join(
        [
            f"Summary: {summary}",
            "",
            "Status: DONE",
            f"Session ID: {session_id}",
            f"Thread ID: {thread_id}",
            f"Task ID: {task_id}",
            "Backend: codex",
            f"Repo: {ANDROID_REPO_ROOT}",
            "Workdir: .",
            "",
            "Reply:",
            "Android bot mailbox smoke is waiting for your manual continuation.",
            f"Exact reply token: {reply_token}",
            "",
            "---TASK-STATE-BEGIN---",
            f"thread_id: {thread_id}",
            f"workspace_id: {workspace_id}",
            f"session_id: {session_id}",
            f"session_name: {session_name}",
            f"task_id: {task_id}",
            "backend: codex",
            f"repo_path: {ANDROID_REPO_ROOT}",
            "workdir: .",
            "mode: analysis_only",
            "status: done",
            f"last_summary: Reply with {reply_token}",
            "---TASK-STATE-END---",
        ],
    )

    prompt_text = "\n".join(
        [
            "手机操作提示：",
            "1. 安装本次 debug APK。",
            "2. 打开应用并同步邮件。",
            "3. 进入 Tasks，找到本条 TaskMail 测试任务。",
            f"4. 在回复框中输入精确文本：{reply_token}",
            "5. 点击发送。",
            "6. 不要点击 /status，不要改动大小写。",
            "",
            "发送完成后回我一句：已发送",
        ],
    )

    return {
        "summary": summary,
        "body": body,
        "prompt_text": prompt_text,
    }


def _seed_command(args: argparse.Namespace) -> int:
    bot_config = load_config(args.bot_config)
    user_config = load_config(args.user_config)
    sender = MailClient(bot_config)

    run_token = args.run_name or f"android-bot-smoke-{_timestamp_slug()}-{secrets.token_hex(3)}"
    session_id = f"android_bot_smoke_{_timestamp_slug()}_{secrets.token_hex(2)}"
    thread_id = session_id
    workspace_id = "workspace_android_task_manager"
    session_name = f"Android bot mailbox smoke {run_token}"
    task_id = f"seed_{_timestamp_slug()}_{secrets.token_hex(2)}"
    reply_token = f"BOT_MAILBOX_SMOKE_{secrets.token_hex(6).upper()}"
    subject = f"[DONE][S:{session_id}] [CX] {session_name}"
    output_dir = Path(args.output_dir) / run_token
    output_dir.mkdir(parents=True, exist_ok=True)

    payload = _build_seed_payload(
        session_id=session_id,
        thread_id=thread_id,
        workspace_id=workspace_id,
        session_name=session_name,
        task_id=task_id,
        reply_token=reply_token,
    )

    message_id = sender.send_mail(
        to_addr=user_config.from_addr or user_config.smtp_user or user_config.imap_user,
        subject=subject,
        body=payload["body"],
        headers={
            SYSTEM_MESSAGE_HEADER: SYSTEM_MESSAGE_HEADER_VALUE,
        },
    )

    artifact = {
        "run_token": run_token,
        "subject": subject,
        "session_id": session_id,
        "thread_id": thread_id,
        "workspace_id": workspace_id,
        "task_id": task_id,
        "reply_token": reply_token,
        "seed_message_id": message_id,
        "seed_sent_at": datetime.now().isoformat(timespec="seconds"),
        "bot_mailbox": bot_config.from_addr or bot_config.smtp_user or bot_config.imap_user,
        "user_mailbox": user_config.from_addr or user_config.smtp_user or user_config.imap_user,
        "output_dir": str(output_dir),
        "summary": payload["summary"],
        "prompt_text": payload["prompt_text"],
    }

    _write_json(output_dir / "seed_artifact.json", artifact)
    _write_text(output_dir / "seed_mail.txt", payload["body"] + "\n")
    _write_text(output_dir / "user_prompt.txt", payload["prompt_text"] + "\n")

    print(f"seed artifact: {output_dir / 'seed_artifact.json'}")
    print(f"seed message id: {message_id}")
    print(payload["prompt_text"])
    return 0


def _matches_expected_reply(envelope, artifact: dict[str, Any]) -> bool:
    expected_sender = _normalize(artifact["user_mailbox"])
    expected_token = artifact["reply_token"]
    seed_message_id = artifact["seed_message_id"]
    session_marker = f"[S:{artifact['session_id']}]"

    return (
        _normalize(envelope.from_addr) == expected_sender
        and expected_token in envelope.body_text
        and (
            envelope.in_reply_to == seed_message_id
            or seed_message_id in envelope.references
        )
        and session_marker in envelope.subject
    )


def _validate_reply(envelope, artifact: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    expected_bot_mailbox = _normalize(artifact["bot_mailbox"])
    expected_sender = _normalize(artifact["user_mailbox"])
    seed_message_id = artifact["seed_message_id"]
    session_marker = f"[S:{artifact['session_id']}]"
    reply_token = artifact["reply_token"]

    if _normalize(envelope.to_addr) != expected_bot_mailbox:
        failures.append(
            f"Expected To={artifact['bot_mailbox']}, actual={envelope.to_addr}",
        )
    if _normalize(envelope.from_addr) != expected_sender:
        failures.append(
            f"Expected From={artifact['user_mailbox']}, actual={envelope.from_addr}",
        )
    if envelope.in_reply_to != seed_message_id:
        failures.append(
            f"Expected In-Reply-To={seed_message_id}, actual={envelope.in_reply_to}",
        )
    if seed_message_id not in envelope.references:
        failures.append(
            f"Expected References to contain {seed_message_id}, actual={envelope.references}",
        )
    if session_marker not in envelope.subject:
        failures.append(
            f"Expected Subject to contain {session_marker}, actual={envelope.subject}",
        )
    if reply_token not in envelope.body_text:
        failures.append(
            f"Expected body to contain {reply_token}",
        )

    return failures


def _verify_command(args: argparse.Namespace) -> int:
    artifact_path = Path(args.artifact)
    artifact = json.loads(artifact_path.read_text(encoding="utf-8"))
    bot_config = load_config(args.bot_config)
    deadline = time.time() + args.poll_timeout_seconds
    latest_candidate: dict[str, Any] | None = None
    latest_envelope = None

    while time.time() < deadline:
        for item in reversed(
            _scan_recent_messages(bot_config, scan_limit=args.scan_limit),
        ):
            envelope = item["envelope"]
            if not _matches_expected_reply(envelope, artifact):
                continue
            latest_candidate = {
                "imap_id": item["imap_id"],
                "mail": _envelope_to_dict(envelope),
            }
            latest_envelope = envelope
            break
        if latest_candidate is not None:
            break
        time.sleep(args.poll_interval_seconds)

    if latest_candidate is None:
        print("No matching Android reply was observed before timeout.", file=sys.stderr)
        return 1

    failures = _validate_reply(latest_envelope, artifact)
    output_dir = Path(artifact["output_dir"])
    _write_json(output_dir / "reply_received.json", latest_candidate)

    result = {
        "artifact_path": str(artifact_path),
        "reply_path": str(output_dir / "reply_received.json"),
        "passed": not failures,
        "failures": failures,
    }
    _write_json(output_dir / "verify_result.json", result)

    if failures:
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return 1

    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Seed and verify a TaskMail Android bot-mailbox smoke reply without starting a backend run.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    seed_parser = subparsers.add_parser(
        "seed",
        help="Send a synthetic TaskMail status mail with built-in user instructions.",
    )
    seed_parser.add_argument(
        "--bot-config",
        default=str(DEFAULT_BOT_CONFIG),
        help="Bot mailbox config used to send the seed mail.",
    )
    seed_parser.add_argument(
        "--user-config",
        default=str(DEFAULT_USER_CONFIG),
        help="User mailbox config that receives the seed mail.",
    )
    seed_parser.add_argument(
        "--output-dir",
        default=str(DEFAULT_OUTPUT_DIR),
        help="Directory where seed artifacts are written.",
    )
    seed_parser.add_argument(
        "--run-name",
        help="Optional fixed run name used in the synthetic session title.",
    )
    seed_parser.set_defaults(handler=_seed_command)

    verify_parser = subparsers.add_parser(
        "verify",
        help="Poll the bot mailbox and verify the Android reply against a saved seed artifact.",
    )
    verify_parser.add_argument(
        "--artifact",
        required=True,
        help="Path to seed_artifact.json created by the seed command.",
    )
    verify_parser.add_argument(
        "--bot-config",
        default=str(DEFAULT_BOT_CONFIG),
        help="Bot mailbox config used to read the Android reply.",
    )
    verify_parser.add_argument(
        "--poll-timeout-seconds",
        type=int,
        default=900,
        help="How long to wait for the Android reply.",
    )
    verify_parser.add_argument(
        "--poll-interval-seconds",
        type=int,
        default=10,
        help="Mailbox poll interval while waiting for the Android reply.",
    )
    verify_parser.add_argument(
        "--scan-limit",
        type=int,
        default=120,
        help="How many recent inbox messages to scan on each poll.",
    )
    verify_parser.set_defaults(handler=_verify_command)

    return parser


def main() -> int:
    parser = _build_parser()
    args = parser.parse_args()
    return args.handler(args)


if __name__ == "__main__":
    raise SystemExit(main())
