package com.example.webrtc

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Builds an embedded HTML5 WebRTC PeerConnection engine (STUN + SRTP DTLS-encrypted audio/video)
 * inside an Android WebView so real-time peer-to-peer WebRTC voice & video calls work cleanly
 * between the 2 paired users over Firestore encrypted SDP & ICE candidate signaling.
 */
object WebRtcEngineHtml {

    fun buildPeerPageHtml(isVideoCall: Boolean, isCaller: Boolean): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
              <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                html, body { width: 100%; height: 100%; background: #0B141A; overflow: hidden; font-family: sans-serif; }
                #remoteVideo {
                  width: 100%;
                  height: 100%;
                  object-fit: cover;
                  background: radial-gradient(circle at center, #133832 0%, #0B141A 75%);
                }
                #localVideo {
                  position: absolute;
                  right: 16px;
                  top: 88px;
                  width: 108px;
                  height: 152px;
                  border-radius: 16px;
                  object-fit: cover;
                  border: 2px solid #25D366;
                  box-shadow: 0 8px 24px rgba(0,0,0,0.55);
                  background: #1F2C34;
                  transform: scaleX(-1);
                  display: ${if (isVideoCall) "block" else "none"};
                }
              </style>
            </head>
            <body>
              <video id="remoteVideo" autoplay playsinline></video>
              <video id="localVideo" autoplay muted playsinline></video>
              <script>
                const config = {
                  iceServers: [
                    { urls: "stun:stun.l.google.com:19302" },
                    { urls: "stun:stun1.l.google.com:19302" }
                  ]
                };
                let pc = new RTCPeerConnection(config);
                let localStream = null;
                let gatheredCandidates = [];
                let remoteCandidatesApplied = new Set();

                pc.ontrack = (event) => {
                  const remoteVideo = document.getElementById('remoteVideo');
                  if (event.streams && event.streams[0]) {
                    remoteVideo.srcObject = event.streams[0];
                  }
                };

                pc.onicecandidate = (event) => {
                  if (event.candidate) {
                    gatheredCandidates.push(event.candidate);
                    if (window.AndroidWebRtcBridge) {
                      window.AndroidWebRtcBridge.onIceCandidatesUpdated(JSON.stringify(gatheredCandidates));
                    }
                  }
                };

                pc.onconnectionstatechange = () => {
                  if (window.AndroidWebRtcBridge) {
                    window.AndroidWebRtcBridge.onConnectionState(pc.connectionState);
                  }
                };

                async function startMediaAndSignaling() {
                  try {
                    localStream = await navigator.mediaDevices.getUserMedia({
                      audio: true,
                      video: ${if (isVideoCall) "{ facingMode: 'user' }" else "false"}
                    });
                    document.getElementById('localVideo').srcObject = localStream;
                    localStream.getTracks().forEach(track => pc.addTrack(track, localStream));
                  } catch (err) {
                    if (window.AndroidWebRtcBridge) {
                      window.AndroidWebRtcBridge.onMediaWarning(err.message || "Media unavailable");
                    }
                  }

                  if (${isCaller}) {
                    try {
                      const offer = await pc.createOffer({
                        offerToReceiveAudio: true,
                        offerToReceiveVideo: ${isVideoCall}
                      });
                      await pc.setLocalDescription(offer);
                      if (window.AndroidWebRtcBridge) {
                        window.AndroidWebRtcBridge.onLocalOfferReady(JSON.stringify(offer));
                      }
                    } catch (e) {}
                  } else {
                    if (window.AndroidWebRtcBridge) {
                      window.AndroidWebRtcBridge.onCalleeReady();
                    }
                  }
                }

                window.receiveRemoteOffer = async function(offerJson) {
                  try {
                    const offer = JSON.parse(offerJson);
                    if (!pc.currentRemoteDescription) {
                      await pc.setRemoteDescription(new RTCSessionDescription(offer));
                      const answer = await pc.createAnswer();
                      await pc.setLocalDescription(answer);
                      if (window.AndroidWebRtcBridge) {
                        window.AndroidWebRtcBridge.onLocalAnswerReady(JSON.stringify(answer));
                      }
                    }
                  } catch (e) {}
                };

                window.receiveRemoteAnswer = async function(answerJson) {
                  try {
                    const answer = JSON.parse(answerJson);
                    if (!pc.currentRemoteDescription) {
                      await pc.setRemoteDescription(new RTCSessionDescription(answer));
                    }
                  } catch (e) {}
                };

                window.receiveRemoteCandidates = async function(candidatesJson) {
                  try {
                    const list = JSON.parse(candidatesJson);
                    for (const c of list) {
                      const key = c.candidate || JSON.stringify(c);
                      if (!remoteCandidatesApplied.has(key)) {
                        remoteCandidatesApplied.add(key);
                        await pc.addIceCandidate(new RTCIceCandidate(c));
                      }
                    }
                  } catch (e) {}
                };

                window.setMicMuted = function(muted) {
                  if (localStream) {
                    localStream.getAudioTracks().forEach(t => t.enabled = !muted);
                  }
                };

                window.setCameraOff = function(cameraOff) {
                  if (localStream) {
                    localStream.getVideoTracks().forEach(t => t.enabled = !cameraOff);
                  }
                };

                window.hangupWebRtc = function() {
                  if (localStream) {
                    localStream.getTracks().forEach(t => t.stop());
                  }
                  pc.close();
                };

                startMediaAndSignaling();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configureWebRtcWebView(context: Context, bridge: Any): WebView {
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            addJavascriptInterface(bridge, "AndroidWebRtcBridge")
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }
            }
        }
    }
}
