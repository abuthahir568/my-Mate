package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.CallSession
import com.example.data.model.Vault
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallDeclineRed
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SanctuaryGold
import com.example.webrtc.WebRtcEngineHtml
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CallsTabScreen(
    vault: Vault,
    currentUserId: String,
    callsHistory: List<CallSession>,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit
) {
    val context = LocalContext.current
    var pendingCallType by remember { mutableStateOf<String?>(null) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Proceed with WebRTC signaling even if emulator lacks physical camera/mic hardware
        when (pendingCallType) {
            "video" -> onStartVideoCall()
            "audio" -> onStartAudioCall()
        }
        pendingCallType = null
    }

    fun requestPermissionsAndCall(type: String) {
        val needed = if (type == "video") {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        val allGranted = needed.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            if (type == "video") onStartVideoCall() else onStartAudioCall()
        } else {
            pendingCallType = type
            permissionsLauncher.launch(needed)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Direct Partner Call Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = SanctuaryGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "End-to-End Encrypted WebRTC Calling",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Direct peer-to-peer SRTP voice & video calls exclusively with ${vault.getPartnerName(currentUserId)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { requestPermissionsAndCall("audio") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("start_voice_call_button")
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Voice Call")
                            }
                            Button(
                                onClick = { requestPermissionsAndCall("video") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("start_video_call_button")
                            ) {
                                Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Video Call")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "RECENT ENCRYPTED CALLS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            if (callsHistory.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "No calls yet. Start an encrypted WebRTC voice or video call above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(callsHistory, key = { it.id }) { call ->
                    val isOutgoing = call.callerUid == currentUserId
                    val partnerLabel = if (isOutgoing) call.calleeName else call.callerName
                    val timeStr = call.createdAt?.toDate()?.let {
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(it)
                    } ?: "Just now"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MateAvatar(
                                name = partnerLabel,
                                photoUrl = vault.getPartnerPhoto(currentUserId),
                                isOnline = false,
                                sizeDp = 46
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = partnerLabel.ifBlank { vault.getPartnerName(currentUserId) },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isOutgoing) {
                                            Icons.AutoMirrored.Filled.CallMade
                                        } else {
                                            Icons.AutoMirrored.Filled.CallReceived
                                        },
                                        contentDescription = null,
                                        tint = if (call.state == "declined" || call.state == "missed") {
                                            CallDeclineRed
                                        } else {
                                            CallAcceptGreen
                                        },
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val durFormatted = if (call.durationSeconds > 0) {
                                        " • ${call.durationSeconds}s"
                                    } else ""
                                    Text(
                                        text = "${call.state.replaceFirstChar { it.uppercase() }}$durFormatted • $timeStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { requestPermissionsAndCall(call.callType) }
                            ) {
                                Icon(
                                    imageVector = if (call.callType == "video") Icons.Filled.Videocam else Icons.Filled.Call,
                                    contentDescription = "Redial ${call.callType} call",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { requestPermissionsAndCall("video") },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("calls_fab_video")
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = "Start Video Call")
            }
            FloatingActionButton(
                onClick = { requestPermissionsAndCall("audio") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("calls_fab_audio")
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Start Voice Call")
            }
        }
    }
}

@Composable
fun ActiveWebRtcCallOverlay(
    call: CallSession,
    currentUserId: String,
    safetyFingerprint: String,
    unpackSignalingPayload: (String?) -> String?,
    onPublishLocalOffer: (String) -> Unit,
    onAcceptCallWithAnswer: (String?) -> Unit,
    onPublishIceCandidates: (Boolean, String) -> Unit,
    onEndOrDecline: (Long) -> Unit
) {
    val context = LocalContext.current
    val isCaller = call.callerUid == currentUserId
    val isVideoCall = call.callType == "video"
    val partnerName = if (isCaller) call.calleeName else call.callerName

    var hasCalleeAcceptedUi by remember(call.id) { mutableStateOf(isCaller || call.state == "connected") }
    var elapsedSeconds by remember(call.id) { mutableLongStateOf(0L) }
    var isMicMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }
    var peerConnectionStatus by remember { mutableStateOf("Negotiating E2EE ICE…") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var calleeWebRtcReady by remember { mutableStateOf(false) }

    // Request runtime mic/camera permissions when callee taps Answer
    val calleePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasCalleeAcceptedUi = true
        onAcceptCallWithAnswer(null)
    }

    // Call duration timer once connected
    LaunchedEffect(call.state) {
        if (call.state == "connected") {
            while (true) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    // Feed remote SDP Offer / Answer / ICE Candidates into the WebRTC PeerConnection
    val remoteOfferJson = remember(call.sdpOffer) { unpackSignalingPayload(call.sdpOffer) }
    val remoteAnswerJson = remember(call.sdpAnswer) { unpackSignalingPayload(call.sdpAnswer) }
    val remoteCandidatesJson = remember(call.callerCandidatesJson, call.calleeCandidatesJson) {
        if (isCaller) unpackSignalingPayload(call.calleeCandidatesJson)
        else unpackSignalingPayload(call.callerCandidatesJson)
    }

    LaunchedEffect(webViewRef, calleeWebRtcReady, remoteOfferJson) {
        if (!isCaller && calleeWebRtcReady && !remoteOfferJson.isNullOrBlank()) {
            val escaped = JSONObject.quote(remoteOfferJson)
            webViewRef?.evaluateJavascript("window.receiveRemoteOffer($escaped);", null)
        }
    }

    LaunchedEffect(webViewRef, remoteAnswerJson) {
        if (isCaller && !remoteAnswerJson.isNullOrBlank()) {
            val escaped = JSONObject.quote(remoteAnswerJson)
            webViewRef?.evaluateJavascript("window.receiveRemoteAnswer($escaped);", null)
        }
    }

    LaunchedEffect(webViewRef, remoteCandidatesJson) {
        if (!remoteCandidatesJson.isNullOrBlank()) {
            val escaped = JSONObject.quote(remoteCandidatesJson)
            webViewRef?.evaluateJavascript("window.receiveRemoteCandidates($escaped);", null)
        }
    }

    DisposableEffect(call.id) {
        onDispose {
            webViewRef?.evaluateJavascript("window.hangupWebRtc && window.hangupWebRtc();", null)
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF073B34), Color(0xFF0B141A))
                    )
                )
        ) {
            // Real HTML5 WebRTC PeerConnection Surface once accepted or calling
            if (hasCalleeAcceptedUi) {
                val jsBridge = remember(call.id) {
                    object {
                        @JavascriptInterface
                        fun onLocalOfferReady(sdpJson: String) {
                            onPublishLocalOffer(sdpJson)
                        }

                        @JavascriptInterface
                        fun onCalleeReady() {
                            calleeWebRtcReady = true
                        }

                        @JavascriptInterface
                        fun onLocalAnswerReady(sdpJson: String) {
                            onAcceptCallWithAnswer(sdpJson)
                        }

                        @JavascriptInterface
                        fun onIceCandidatesUpdated(candidatesJson: String) {
                            onPublishIceCandidates(isCaller, candidatesJson)
                        }

                        @JavascriptInterface
                        fun onConnectionState(state: String) {
                            peerConnectionStatus = "WebRTC Peer: $state"
                        }

                        @JavascriptInterface
                        fun onMediaWarning(msg: String) {
                            peerConnectionStatus = "Signaling active ($msg)"
                        }
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        WebRtcEngineHtml.configureWebRtcWebView(ctx, jsBridge).also { wv ->
                            wv.loadDataWithBaseURL(
                                "https://mymate.local/",
                                WebRtcEngineHtml.buildPeerPageHtml(isVideoCall, isCaller),
                                "text/html",
                                "utf-8",
                                null
                            )
                            webViewRef = wv
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Call Identity & E2EE Safety Fingerprint Overlay
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 24.dp, start = 20.dp, end = 20.dp)
            ) {
                Surface(
                    color = Color(0x99000000),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = SanctuaryGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "E2EE SRTP • Safety $safetyFingerprint",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                if (!isVideoCall || call.state != "connected") {
                    MateAvatar(
                        name = partnerName,
                        photoUrl = null,
                        isOnline = true,
                        sizeDp = 96
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = partnerName.ifBlank { "My Mate" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                val timerText = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
                Text(
                    text = when {
                        call.state == "connected" -> "$timerText • $peerConnectionStatus"
                        isCaller -> "Ringing your Mate (${call.callType})…"
                        else -> "Incoming Encrypted ${call.callType.replaceFirstChar { it.uppercase() }} Call…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8C7CE)
                )
            }

            // Bottom Call Controls
            Surface(
                color = Color(0xCC121B22),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .fillMaxWidth()
            ) {
                if (!hasCalleeAcceptedUi && !isCaller && call.state == "ringing") {
                    // Incoming Call Ringing Controls: Decline vs Accept
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp, horizontal = 28.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingActionButton(
                            onClick = { onEndOrDecline(0L) },
                            shape = CircleShape,
                            containerColor = CallDeclineRed,
                            contentColor = Color.White,
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("decline_incoming_call_button")
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "Decline Call")
                        }

                        FloatingActionButton(
                            onClick = {
                                val perms = if (isVideoCall) {
                                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                                } else {
                                    arrayOf(Manifest.permission.RECORD_AUDIO)
                                }
                                calleePermLauncher.launch(perms)
                            },
                            shape = CircleShape,
                            containerColor = CallAcceptGreen,
                            contentColor = Color.White,
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("accept_incoming_call_button")
                        ) {
                            Icon(
                                imageVector = if (isVideoCall) Icons.Filled.Videocam else Icons.Filled.Call,
                                contentDescription = "Accept Call"
                            )
                        }
                    }
                } else {
                    // Active / Outgoing Controls: Mute Mic, Toggle Camera, Hang Up
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isMicMuted = !isMicMuted
                                webViewRef?.evaluateJavascript("window.setMicMuted($isMicMuted);", null)
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isMicMuted) Color.White else Color(0xFF233138))
                        ) {
                            Icon(
                                imageVector = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = "Toggle Microphone",
                                tint = if (isMicMuted) Color.Black else Color.White
                            )
                        }

                        if (isVideoCall) {
                            IconButton(
                                onClick = {
                                    isCameraOff = !isCameraOff
                                    webViewRef?.evaluateJavascript("window.setCameraOff($isCameraOff);", null)
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(if (isCameraOff) Color.White else Color(0xFF233138))
                            ) {
                                Icon(
                                    imageVector = if (isCameraOff) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                                    contentDescription = "Toggle Camera",
                                    tint = if (isCameraOff) Color.Black else Color.White
                                )
                            }
                        }

                        FloatingActionButton(
                            onClick = {
                                webViewRef?.evaluateJavascript("window.hangupWebRtc && window.hangupWebRtc();", null)
                                onEndOrDecline(elapsedSeconds)
                            },
                            shape = CircleShape,
                            containerColor = CallDeclineRed,
                            contentColor = Color.White,
                            modifier = Modifier
                                .size(60.dp)
                                .testTag("hangup_call_button")
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "End Call")
                        }
                    }
                }
            }
        }
    }
}
