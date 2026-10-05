#!/usr/bin/env python3
"""Local prerequisite check. No model calls, workflow runs, sends or credential output."""
import os
import shutil
import subprocess
import sys

errors=[]
for name in ('GOOGLE_API_KEY',):
    if not os.environ.get(name) or os.environ[name].startswith('your-'):errors.append(f'{name} is missing in .env')
if os.environ.get('JCLAW_DECIDER','jev')=='jev' and not (os.environ.get('TYPESAFE_API_KEY') or os.environ.get('JEV_API_KEY')):
    errors.append('TYPESAFE_API_KEY is missing for rounds 5–7; use JCLAW_DECIDER=gemini only for the explicit comparison')
for binary in ('java','claude','codex'):
    if not shutil.which(binary):errors.append(f'{binary} is not on PATH')
lf=('LANGFUSE_PUBLIC_KEY','LANGFUSE_SECRET_KEY')
if any(os.environ.get(k) for k in lf) and not all(os.environ.get(k) for k in lf):errors.append('Langfuse needs both public and secret keys')
if errors:
    print('\n'.join('FAIL: '+e for e in errors),file=sys.stderr);sys.exit(1)
print('PASS: local keys and runtime commands are configured; credentials were not printed.')
print('Models: Gemini '+os.environ.get('JCLAW_FLASH','3.7')+' Flash; Jev jev-1.13.0; '+os.environ.get('JCLAW_CLAUDE_MODEL','claude-opus-4-6')+'; '+os.environ.get('JCLAW_CODEX_MODEL','gpt-6.1-sol')+' / '+os.environ.get('JCLAW_CODEX_EFFORT','low'))
print('CLI subscription login and live provider availability are checked by the rehearsal, not inferred from installed binaries.')
print('Langfuse: '+('configured' if all(os.environ.get(k) for k in lf) else 'not configured; required for round 7 backend inspection'))
