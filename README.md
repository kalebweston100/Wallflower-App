# Wallflower

An Android app for **encrypted, serverless, one-to-one chat with people
nearby**. Phones discover each other and talk directly over Wi-Fi Direct, so
there is no internet connection, no account, and no server in the middle.

> **Shipped app (2021–2023).** Wallflower was published on the Google Play
> Store and went through several releases (current source is v2.1). It was
> eventually delisted after I stopped keeping up with Play's target-API
> requirements, but the full source is here.

## How it works

1. **Pick who to meet.** Enter a 6-digit code you've agreed on with a friend,
   or switch on *broadcast* mode to match with anyone else nearby who has it
   on.
2. **Discovery.** A foreground service advertises a Wi-Fi Direct UPnP service
   whose UUID is derived from the code (`UUID.nameUUIDFromBytes("Wallflower-" + code)`),
   and simultaneously searches for that same UUID. Only devices using the same
   code find each other. You can leave the app, and you'll get a notification
   when a match connects.
3. **Connection.** Once Wi-Fi Direct forms a group, the two devices open a
   TCP socket on the group's local network (`ServerSocketChannel` on the group
   owner, `SocketChannel` on the client).
4. **Key exchange.** Each device generates an RSA key pair per session and
   sends its public key to the peer.
5. **Encrypted chat.** Every message is encrypted with the peer's public key
   using RSA-OAEP (SHA-256 / MGF1-SHA-256). Messages are capped at 64
   characters so each one fits in a single RSA block. Sends go through a
   single-thread executor so writes never interleave on the socket.

## Code tour

| File | Responsibility |
|------|----------------|
| `MainActivity` | Code entry, broadcast toggle, permissions, user policy |
| `ConnectService` | Foreground service: Wi-Fi Direct service advertisement + discovery |
| `WifiReceiver`, `ServiceReceiver`, `ChatReceiver` | Broadcast receivers for P2P state changes and disconnects |
| `ChatActivity` | Chat UI |
| `Connection` | Socket setup/teardown, send/receive loop, key exchange |
| `KeyHandler` | RSA key generation and OAEP encrypt/decrypt |
| `ThreadExecute` | Serializes socket writes |

## Building

Open in Android Studio, or:

```sh
./gradlew assembleDebug
```

Requires a physical device with Wi-Fi Direct (Android 10+, `minSdk 29`).
Emulators don't support Wi-Fi Direct, so testing needs two real phones.

## What I'd do differently

- **Authenticate the key exchange.** Public keys are exchanged unauthenticated,
  so an active attacker on the Wi-Fi Direct link could man-in-the-middle the
  session. Deriving a key from the shared code (e.g. a PAKE such as SPAKE2)
  would fix that and make the code actually protect the conversation, not
  just the matching.
- **Use hybrid encryption.** Encrypting messages directly with RSA is what
  forces the 64-character limit. An ephemeral ECDH exchange followed by
  AES-GCM (or just the Signal protocol primitives) removes the limit and
  gives forward secrecy.
- **The code space is small.** 6 digits is only 10^6 UUIDs, and they're
  advertised in the clear, so a nearby device could enumerate them.
- **Tests and CI.** None exist; the networking code would benefit from being
  separated from Android framework classes so it can be unit-tested.
