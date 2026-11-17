#!/usr/bin/env python3
"""Read-only, full question quality snapshot using the current Java rule constants."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', default='lexaid')
    parser.add_argument('--container', default='lexaid-mysql')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_]+', args.database):
        parser.error('database must be a simple MySQL identifier')
    root = Path(__file__).resolve().parents[1]
    sources = root / 'backend/src/main/java/com/lexaid/exam'
    with tempfile.TemporaryDirectory(prefix='lexaid-quality-') as temp:
        bridge = Path(temp) / 'QualitySql.java'
        bridge.write_text('''import com.lexaid.exam.QuestionQuality;
import com.lexaid.exam.QuestionSubjects;
class QualitySql {
 public static void main(String[] args) {
  System.out.print("SELECT JSON_OBJECT('id',q.id,'source',(SELECT GROUP_CONCAT(DISTINCT dataset ORDER BY dataset) FROM question_source WHERE question_id=q.id),"
   + "'reason'," + QuestionQuality.REASON + ",'contentHash'," + QuestionQuality.HASH + ")"
   + QuestionSubjects.FROM_QUESTION + " ORDER BY q.id;");
 }
}''')
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp,
                        str(sources / 'QuestionSubjects.java'), str(sources / 'QuestionQuality.java'), str(bridge)], check=True)
        sql = subprocess.check_output(['java', '-cp', temp, 'QualitySql'], text=True)
    command = ['docker', 'exec', '-i', args.container, 'sh', '-c',
               'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B --raw -u "$MYSQL_USER" ' + args.database]
    result = subprocess.run(command, input='START TRANSACTION READ ONLY;\n' + sql + '\nCOMMIT;',
                            text=True, capture_output=True, check=True)
    rows = [json.loads(line) for line in result.stdout.splitlines() if line]
    manifest = ''.join(json.dumps(row, ensure_ascii=False, sort_keys=True) + '\n' for row in rows).encode()
    counts = Counter(row['reason'] for row in rows)
    summary = {
        'snapshotAt': datetime.now(timezone.utc).isoformat(), 'database': args.database,
        'scope': 'Full automated rule scan; not full legal approval or human dual review',
        'total': len(rows), 'counts': dict(counts),
        'eligibleBySource': dict(Counter(row['source'] or 'seed' for row in rows if row['reason'] == 'ELIGIBLE')),
        'querySha256': hashlib.sha256(sql.encode()).hexdigest(),
        'manifestSha256': hashlib.sha256(manifest).hexdigest(),
    }
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'manifest.jsonl.gz').write_bytes(gzip.compress(manifest, mtime=0))
    (args.output / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
