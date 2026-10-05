#!/usr/bin/env python3
"""Create a fresh, named rehearsal; never reset personal or earlier demo history."""
from datetime import datetime, timezone
from pathlib import Path
import json
import shutil
import uuid

repo = Path(__file__).resolve().parents[1]
seed_names = (
    "2e3eea73-6e25-5ab8-8159-d986db9e0f38",
    "2e72c8ef-dd58-524c-adb5-4e0c39e9a865",
    "87ac1ec3-7e7f-56bd-a18f-3e731e57a676",
)
session = repo / "build" / "demo" / (datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:6])
for name in ("memory-skills", "workflows", "guardrails-observability"):
    documents = session / name / "memory" / "documents"
    documents.mkdir(parents=True)
    for seed in seed_names:
        shutil.copy2(repo / "memory" / "documents" / seed, documents / seed)
(session / "manifest.json").write_text(json.dumps({"seed_documents": list(seed_names), "created": datetime.now(timezone.utc).isoformat(), "fictional": True}, indent=2) + "\n")
(repo / "build" / "demo" / "current").write_text(str(session) + "\n")
print(f"Fresh fictional demo: {session}")
print("Run ./jclaw demo 1 through ./jclaw demo 7. Round 3/4 share history; round 6/7 share sent facts.")
print("Personal memory and earlier demo sessions were retained.")
