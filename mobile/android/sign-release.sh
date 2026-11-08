#!/bin/sh
set -eu

# The signing identity is reused on every build; Android requires it for updates.
project=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
unsigned=${1:-"$project/app/build/outputs/apk/release/app-release-unsigned.apk"}
sdk=${ANDROID_HOME:-"$HOME/Library/Android/sdk"}
tools="$sdk/build-tools/36.0.0"
key_dir=${LEXAID_ANDROID_SIGNING_DIR:-"$HOME/.config/lexaid/android-signing"}
destination="$project/../../release"
apk=LexAid-1.0.0-Android.apk
test -f "$unsigned"
mkdir -p "$destination"
umask 077
mkdir -p "$key_dir"
if [ ! -f "$key_dir/release.p12" ]; then
    if [ -e "$key_dir/passphrase" ]; then
        echo "The signing key is missing. Restore the key before signing another release." >&2
        exit 1
    fi
    (
        set -C
        openssl rand -hex 32 > "$key_dir/passphrase"
        trap 'test -f "$key_dir/release.p12" || rm -f "$key_dir/passphrase" "$key_dir/release.next"' EXIT
        keytool -genkeypair -alias lexaid -storetype PKCS12 -keystore "$key_dir/release.next" \
            -storepass:file "$key_dir/passphrase" -keypass:file "$key_dir/passphrase" \
            -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=LexAid Android'
        mv "$key_dir/release.next" "$key_dir/release.p12"
        trap - EXIT
    )
fi
test -s "$key_dir/passphrase"
tmp=$(mktemp -d "$destination/.android-sign.XXXXXXXX")
trap 'rm -rf "$tmp"' EXIT
"$tools/zipalign" -f 4 "$unsigned" "$tmp/aligned.apk"
"$tools/apksigner" sign --ks "$key_dir/release.p12" --ks-key-alias lexaid \
    --ks-pass "file:$key_dir/passphrase" --out "$tmp/$apk" "$tmp/aligned.apk"
"$tools/apksigner" verify --verbose --print-certs "$tmp/$apk"
"$tools/zipalign" -c 4 "$tmp/$apk"
(cd "$tmp" && shasum -a 256 "$apk" > "$apk.sha256")
chmod 644 "$tmp/$apk" "$tmp/$apk.sha256"
mv "$tmp/$apk" "$tmp/$apk.sha256" "$destination/"
