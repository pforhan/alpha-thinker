# Eval receiver TLS material

`cert.pem` and `key.pem` are a self-signed certificate/key pair for
`tools/eval_receiver.py`. They are checked in on purpose: the app pins this exact
certificate (`EvalRemote.pinnedCertificate`), so every checkout can receive rows
from every installed app without generating or copying a new certificate first.

- The **private key is public**. It identifies this tool on a private LAN, not a
  real service; anyone with repository access can impersonate the receiver.
- The certificate is valid for 10 years and its SAN covers `localhost` and
  `127.0.0.1`. The app disables hostname verification only on this one pinned
  connection, because a LAN address (`192.168.x.x`) cannot be predicted when the
  certificate is generated; the pinned public key is what proves identity.
- If you regenerate the pair, update `EvalRemote.pinnedCertificate` in
  `shared/src/commonMain/kotlin/alphainterplanetary/thinker/eval/EvalRemote.kt`.
  `EvalRemotePinningTest` fails when the pinned copy and `cert.pem` differ.

Regenerate with:

```bash
openssl req -x509 -newkey rsa:2048 -sha256 -days 3650 -nodes \
  -keyout tools/eval_tls/key.pem -out tools/eval_tls/cert.pem \
  -subj "/CN=Alpha Thinker Eval Receiver" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
  -addext "basicConstraints=critical,CA:FALSE" \
  -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=serverAuth"
```
