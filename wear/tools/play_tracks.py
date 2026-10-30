#!/usr/bin/env python3
"""
Read-only Google Play status: lists every track and its releases for io.github.hamzacoder53.wavelet.

  python3 -I wear/tools/play_tracks.py [path/to/service-account.json]

Credentials: argument, else $WAVELET_PLAY_CREDENTIALS, else ~/.tumbler/play-service-account.json.
Opens a temporary edit to read, then deletes it, so nothing on Play changes.
No third-party packages: the OAuth JWT is signed with the system `openssl`.
"""
import base64, json, os, subprocess, sys, tempfile, time, urllib.error, urllib.parse, urllib.request

PACKAGE = 'io.github.hamzacoder53.wavelet'
API = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' + PACKAGE


def b64(data): return base64.urlsafe_b64encode(data).rstrip(b'=')


def access_token(cred_path):
    sa = json.load(open(cred_path))
    now = int(time.time())
    header = b64(json.dumps({'alg': 'RS256', 'typ': 'JWT'}).encode())
    claims = b64(json.dumps({
        'iss': sa['client_email'], 'scope': 'https://www.googleapis.com/auth/androidpublisher',
        'aud': sa['token_uri'], 'iat': now, 'exp': now + 600}).encode())
    signing_input = header + b'.' + claims
    # Write the key to a private temp file only for openssl, then delete it.
    fd, key_path = tempfile.mkstemp(dir=os.path.dirname(os.path.abspath(cred_path)))
    try:
        os.fchmod(fd, 0o600)
        os.write(fd, sa['private_key'].encode()); os.close(fd)
        sig = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', key_path], input=signing_input,
                             capture_output=True, check=True).stdout
    finally:
        os.remove(key_path)
    jwt = signing_input + b'.' + b64(sig)
    body = urllib.parse.urlencode({'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer', 'assertion': jwt}).encode()
    return json.load(urllib.request.urlopen(sa['token_uri'], body))['access_token']


def call(token, method, url):
    req = urllib.request.Request(url, method=method, headers={'Authorization': 'Bearer ' + token},
                                 data=b'' if method == 'POST' else None)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        sys.exit('%s %s -> HTTP %d: %s' % (method, url.split(PACKAGE)[-1], e.code, e.read().decode()[:600]))


def main():
    cred = (sys.argv[1] if len(sys.argv) > 1 else None) or os.environ.get('WAVELET_PLAY_CREDENTIALS') \
        or os.path.expanduser('~/.tumbler/play-service-account.json')
    token = access_token(cred)
    edit = call(token, 'POST', API + '/edits')
    try:
        tracks = call(token, 'GET', API + '/edits/%s/tracks' % edit['id']).get('tracks', [])
        for t in tracks:
            print('track: %s' % t['track'])
            for r in t.get('releases', []) or []:
                print('   release %-12s status=%-10s versionCodes=%s' % (
                    repr(r.get('name', '')), r.get('status'), r.get('versionCodes', [])))
        if not tracks: print('(no tracks returned)')
    finally:
        call(token, 'DELETE', API + '/edits/%s' % edit['id'])


if __name__ == '__main__':
    main()
