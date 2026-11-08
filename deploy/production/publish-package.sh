#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
name=${1:?Pass a release filename}
[[ $name =~ ^LexAid-[A-Za-z0-9._-]+\.(apk|dmg|exe|ipa|zip)$ ]] || exit 2
cd packages
read -r expected recorded < "$name.sha256"
[[ $expected =~ ^[a-fA-F0-9]{64}$ && ${recorded#\*} == "$name" ]] || exit 2
printf '%s  %s\n' "$expected" "$name" | sha256sum -c -
target=../web/downloads
mkdir -p "$target"
if [[ -e $target/$name ]]; then
  (cd "$target" && printf '%s  %s\n' "$expected" "$name" | sha256sum -c -)
fi
for artifact in "$name" "$name.sha256"; do
  chmod 644 "$artifact"
  if [[ $artifact -ef $target/$artifact ]]; then continue; fi
  ln "$artifact" "$target/.$artifact.$$.new"
  mv -fT "$target/.$artifact.$$.new" "$target/$artifact"
done
