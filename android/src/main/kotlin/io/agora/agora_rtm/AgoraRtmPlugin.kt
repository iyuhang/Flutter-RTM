package io.agora.agorartm

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.agora.rtm.*
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class AgoraRtmPlugin: MethodCallHandler {
  private val registrar: Registrar
  private val methodChannel: MethodChannel
  private val eventHandler: Handler
  private var nextClientIndex: Int = 0
  private var nextChannelIndex: Int = 0
  private var clients = HashMap<Int, RtmClient>()
  private var channels = HashMap<Int, RtmChannel>()

  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val channel = MethodChannel(registrar.messenger(), "io.agora.rtm")
      val plugin = AgoraRtmPlugin(registrar, channel)
      channel.setMethodCallHandler(plugin)
    }
  }

  constructor(registrar: Registrar, channel: MethodChannel) {
    this.registrar = registrar
    this.methodChannel = channel
    this.eventHandler = Handler(Looper.getMainLooper())
  }

  private fun invokeMethod(method: String, arguments: HashMap<String, Any>) {
    eventHandler.post(Runnable {
      methodChannel.invokeMethod(method, arguments)
    })
  }

  private fun getActiveContext(): Context {
    return when {
      (registrar.activity() == null) -> registrar.context()
      else -> registrar.activity()
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    val callMethod = call.method
    val callArguments = call.arguments
    if (callArguments !is Map<*, *>) {
      result.error("Wrong arguments type", null, null)
      return
    }

    when (callMethod) {
      // client
      "AgoraRtmClient_createInstance" -> {
        val appId = this.stringFromArguments(callArguments, "appId")
        try {
          var client: RtmClient? = null
          client = RtmClient.createInstance(getActiveContext(), appId, object : RtmClientListener {
            override fun onConnectionStateChanged(state: Int, reason: Int) {
              val clientIndex = indexOfClient(client!!)
              if (clientIndex != null) {
                val arguments = rtmClientArguments(clientIndex, hashMapOf("state" to state, "reason" to reason))
                invokeMethod("AgoraRtmClient_onConnectionStateChanged", arguments)
              }
            }

            override fun onMessageReceived(message: RtmMessage, peerId: String) {
              val clientIndex = indexOfClient(client!!)
              if (clientIndex != null) {
                val arguments = rtmClientArguments(clientIndex, hashMapOf("message" to mapFromMessage(message), "peerId" to peerId))
                invokeMethod("AgoraRtmClient_onMessageReceived", arguments)
              }
            }

            override fun onTokenExpired() {
              val clientIndex = indexOfClient(client!!)
              if (clientIndex != null) {
                val arguments = rtmClientArguments(clientIndex, null)
                invokeMethod("AgoraRtmClient_onTokenExpired", arguments)
              }
            }
          })
          if (client == null) {
            result.success(-1)
          }
          val key = nextClientIndex
          clients[key] = client
          nextClientIndex ++
          result.success(key)
        } catch (e: Exception) {
          result.success(-1)
        }
      }
      "AgoraRtmClient_login" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_login", arguments)
          return
        }
        val token = stringFromArguments(callArguments, "token")
        val userId = stringFromArguments(callArguments, "userId")
        client.login(token, userId, object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmClient_login", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            invokeMethod("AgoraRtmClient_login", arguments)
          }
        })
      }
      "AgoraRtmClient_logout" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_logout", arguments)
          return
        }
        client.logout(object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmClient_logout", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            invokeMethod("AgoraRtmClient_logout", arguments)
          }
        })
      }
      "AgoraRtmClient_queryPeersOnlineStatus" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_queryPeersOnlineStatus", arguments)
          return
        }
        val peerIdArray: List<String>? = listFromArguments(callArguments, "peerIds")
        val s = HashSet<String>()
        peerIdArray?.apply {
          s.addAll(this)
        }

        client.queryPeersOnlineStatus(s, object : ResultCallback<Map<String, Boolean>> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmClient_queryPeersOnlineStatus", arguments)
          }

          override fun onSuccess(p0: Map<String, Boolean>?) {
            val arguments: Any?
            arguments = when {
              (p0 != null) -> rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0, "results" to p0))
              else -> rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            }

            invokeMethod("AgoraRtmClient_queryPeersOnlineStatus", arguments)
          }
        })
      }
      "AgoraRtmClient_sendMessageToPeer" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_sendMessageToPeer", arguments)
          return
        }
        val peerId = stringFromArguments(callArguments, "peerId")
        val messageMap = mapFromArguments(callArguments, "message")
        val message = messageFromMap(messageMap, client)
        client.sendMessageToPeer(peerId, message, object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmClient_sendMessageToPeer", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            invokeMethod("AgoraRtmClient_sendMessageToPeer", arguments)
          }
        })
      }
      "AgoraRtmClient_createChannel" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_createChannel", arguments)
          return
        }

        val channelId = stringFromArguments(callArguments, "channelId")
        try {
          var channel: RtmChannel? = null
          channel = client.createChannel(channelId, object : RtmChannelListener {
            override fun onMemberJoined(member: RtmChannelMember?) {
              val channelIndex = indexOfChannel(channel!!)
              if (channelIndex != null && member != null) {
                val arguments = rtmChannelArguments(channelIndex, hashMapOf("member" to mapFromMember(member)))
                invokeMethod("AgoraRtmChannel_onMemberJoined", arguments)
              }
            }

            override fun onMemberLeft(member: RtmChannelMember?) {
              val channelIndex = indexOfChannel(channel!!)
              if (channelIndex != null && member != null) {
                val arguments = rtmChannelArguments(channelIndex, hashMapOf("member" to mapFromMember(member)))
                invokeMethod("AgoraRtmChannel_onMemberLeft", arguments)
              }
            }

            override fun onMessageReceived(message: RtmMessage?, fromMember: RtmChannelMember?) {
              val channelIndex = indexOfChannel(channel!!)
              if (channelIndex != null && message != null && fromMember != null) {
                val arguments = rtmChannelArguments(channelIndex, hashMapOf("member" to mapFromMember(fromMember), "message" to mapFromMessage(message)))
                invokeMethod("AgoraRtmChannel_onMessageReceived", arguments)
              }
            }
          })
          if (channel == null) {
            result.success(-1)
          }
          val key = nextChannelIndex
          channels[key] = channel
          nextChannelIndex ++
          result.success(key)
        } catch (e: Exception) {
          result.success(-1)
        }
      }
      // channel
      "AgoraRtmChannel_join" -> {
        val channelIndex = intFromArguments(callArguments, "channelIndex")
        val channel = channels[channelIndex]
        if (channel == null) {
          val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmChannel_join", arguments)
          return
        }
        channel.join(object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmChannel_join", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to 0))
            invokeMethod("AgoraRtmChannel_join", arguments)
          }
        })
      }
      "AgoraRtmChannel_leave" -> {
        val channelIndex = intFromArguments(callArguments, "channelIndex")
        val channel = channels[channelIndex]
        if (channel == null) {
          val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmChannel_leave", arguments)
          return
        }
        channel.leave(object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmChannel_leave", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to 0))
            invokeMethod("AgoraRtmChannel_leave", arguments)
          }
        })
      }
      "AgoraRtmChannel_sendMessage" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        val channelIndex = intFromArguments(callArguments, "channelIndex")
        val channel = channels[channelIndex]
        if (client == null || channel == null) {
          val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmChannel_sendMessage", arguments)
          return
        }
        val messageMap = mapFromArguments(callArguments, "message")
        val message = messageFromMap(messageMap, client)
        channel.sendMessage(message, object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmChannel_sendMessage", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to 0))
            invokeMethod("AgoraRtmChannel_sendMessage", arguments)
          }
        })
      }
      "AgoraRtmChannel_getMembers" -> {
        val channelIndex = intFromArguments(callArguments, "channelIndex")
        val channel = channels[channelIndex]
        if (channel == null) {
          val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmChannel_getMembers", arguments)
          return
        }
        channel.getMembers(object : ResultCallback<List<RtmChannelMember>> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmChannel_getMembers", arguments)
          }

          override fun onSuccess(list: List<RtmChannelMember>?) {
            val membersList = ArrayList<Map<String, String>>()
            if (list != null) {
              for (member in list) {
                membersList.add(hashMapOf("userId" to member.userId, "channelId" to member.channelId))
              }
            }
            val arguments = rtmChannelArguments(channelIndex, hashMapOf("errorCode" to 0, "members" to membersList))
            invokeMethod("AgoraRtmChannel_getMembers", arguments)
          }
        })
      }
      "AgoraRtmChannel_release" -> {
        val channelIndex = intFromArguments(callArguments, "channelIndex")
        val channel = channels[channelIndex]
        if (channel == null) {
          result.success(-1)
          return
        }
        channel.release()
        channels.remove(channelIndex)
        result.success(0)
      }
      "AgoraRtmClient_setLocalUserAttributes" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_setLocalUserAttributes", arguments)
          return
        }
        val attributes: List<Map<String, String>>? = listFromArguments(callArguments, "attributes")
        var localUserAttributes = ArrayList<RtmAttribute>()
        attributes?.forEach {
          var rtmAttribute = RtmAttribute()
          rtmAttribute.key = it["key"]
          rtmAttribute.value = it["value"]
          localUserAttributes.add(rtmAttribute)
        }
        client.setLocalUserAttributes(localUserAttributes.toList(), object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmClient_setLocalUserAttributes", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            invokeMethod("AgoraRtmClient_setLocalUserAttributes", arguments)
          }
        })
      }
      "AgoraRtmClient_addOrUpdateLocalUserAttributes" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_addOrUpdateLocalUserAttributes", arguments)
          return
        }
        val attributes: List<Map<String, String>>? = listFromArguments(callArguments, "attributes")
        val localUserAttributes = ArrayList<RtmAttribute>()
        attributes?.forEach {
          var rtmAttribute = RtmAttribute()
          rtmAttribute.key = it["key"]
          rtmAttribute.value = it["value"]
          localUserAttributes.add(rtmAttribute)
        }
        client.addOrUpdateLocalUserAttributes(localUserAttributes.toList(), object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmClient_addOrUpdateLocalUserAttributes", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            invokeMethod("AgoraRtmClient_addOrUpdateLocalUserAttributes", arguments)
          }
        })
      }
      "AgoraRtmClient_deleteLocalUserAttributesByKeys" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_deleteLocalUserAttributesByKeys", arguments)
          return
        }
        val keys: List<String>? = listFromArguments(callArguments, "keys")
        client.deleteLocalUserAttributesByKeys(keys, object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmClient_deleteLocalUserAttributesByKeys", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            invokeMethod("AgoraRtmChannel_deleteLocalUserAttributesByKeys", arguments)
          }
        })
      }
      "AgoraRtmClient_clearLocalUserAttributes" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_clearLocalUserAttributes", arguments)
          return
        }
        client.clearLocalUserAttributes(object : ResultCallback<Void> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmClient_clearLocalUserAttributes", arguments)
          }

          override fun onSuccess(p0: Void?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            invokeMethod("AgoraRtmClient_clearLocalUserAttributes", arguments)
          }
        })
      }
      "AgoraRtmClient_getUserAttributes" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_getUserAttributes", arguments)
          return
        }
        val userId: String? = stringFromArguments(callArguments, "userId")
        client.getUserAttributes(userId, object : ResultCallback<List<RtmAttribute>> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmClient_getUserAttributes", arguments)
          }

          override fun onSuccess(args: List<RtmAttribute>?) {
            val arguments: Any?
            arguments = when {
              (args != null) -> rtmClientArguments(clientIndex,
                      hashMapOf(
                              "errorCode" to 0,
                              "results" to hashMapOf(
                                        "userId" to userId,
                                        "attributes" to extractFromAttributes(args)
                              )
                      )
              )
              else -> rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            }
            invokeMethod("AgoraRtmClient_getUserAttributes", arguments)
          }
        })
      }
      "AgoraRtmClient_getUserAttributesByKeys" -> {
        val clientIndex = intFromArguments(callArguments, "clientIndex")
        val client = clients[clientIndex]
        if (client == null) {
          val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to -1))
          invokeMethod("AgoraRtmClient_getUserAttributesByKeys", arguments)
          return
        }
        val userId: String? = stringFromArguments(callArguments, "userId")
        val keys: List<String>? = listFromArguments(callArguments, "keys")
        client.getUserAttributesByKeys(userId, keys, object : ResultCallback<List<RtmAttribute>> {
          override fun onFailure(p0: ErrorInfo?) {
            val arguments = rtmClientArguments(clientIndex, hashMapOf("errorCode" to p0!!.errorCode))
            invokeMethod("AgoraRtmClient_getUserAttributesByKeys", arguments)
          }

          override fun onSuccess(args: List<RtmAttribute>?) {
            val arguments: Any?
            arguments = when {
              (args != null) -> rtmClientArguments(clientIndex,
                      hashMapOf(
                              "errorCode" to 0,
                              "results" to hashMapOf(
                                      "userId" to userId,
                                      "attributes" to extractFromAttributes(args)
                              )
                      )
              )
              else -> rtmClientArguments(clientIndex, hashMapOf("errorCode" to 0))
            }
            invokeMethod("AgoraRtmClient_getUserAttributesByKeys", arguments)
          }
        })
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  // client
  private fun indexOfClient(client: RtmClient) : Int? {
    for (key in clients.keys) {
      if (clients[key] == client) {
        return key
      }
    }
    return -1
  }

  private fun rtmClientArguments(clientIndex: Int, arguments: HashMap<String, Any>?) : HashMap<String, Any> {
    val map: HashMap<String, Any> = when {
      (arguments != null) -> arguments
      else -> HashMap()
    }
    map["objIndex"] = clientIndex
    map["obj"] = "AgoraRtmClient"
    return map
  }

  // channel
  private fun indexOfChannel(channel: RtmChannel) : Int? {
    for (key in channels.keys) {
      if (channels[key] == channel) {
        return key
      }
    }
    return -1
  }

  private fun rtmChannelArguments(channelIndex: Int, arguments: HashMap<String, Any>?) : HashMap<String, Any> {
    val map: HashMap<String, Any> = when {
      (arguments != null) -> arguments
      else -> HashMap()
    }
    map["objIndex"] = channelIndex
    map["obj"] = "AgoraRtmChannel"
    return map
  }

  // helper
  private fun stringFromArguments(arguments: Map<*, *>, key: String): String? {
    val value = arguments[key]
    return when {
      (value is String) -> value
      else -> null
    }
  }

  private fun intFromArguments(arguments: Map<*, *>, key: String): Int {
    val value = arguments[key]
    return when {
      (value is Int) -> value
      else -> -1
    }
  }

  private inline fun <reified T> listFromArguments(arguments: Map<*, *>, key: String): List<T>? {
    val value = arguments[key]
    return when {
      (value is List<*>) -> value.asListOfType()
      else -> null
    }
  }

  private inline fun <reified T> List<*>.asListOfType(): List<T>? =
          if (all { it is T })
            @Suppress("UNCHECKED_CAST")
            this as List<T> else
            null

  private fun mapFromArguments(arguments: Map<*, *>, key: String): Map<*, *>? {
    val value = arguments[key]

    return when {
      (value is Map<*, *>) -> value
      else -> null
    }
  }

  //
  private fun mapFromMessage(message: RtmMessage): Map<String, *> {
    return hashMapOf("text" to message.text)
  }

  private fun messageFromMap(map: Map<*, *>?, client: RtmClient): RtmMessage {
    var text: String? = null
    if (map != null) {
      text = stringFromArguments(map, "text")
    }
    if (text == null) {
      text = ""
    }
    val message = client.createMessage()
    message.text = text
    return  message
  }

  private fun mapFromMember(member: RtmChannelMember): Map<String, *> {
    return hashMapOf("userId" to member.userId, "channelId" to member.channelId)
  }

  private fun extractFromAttributes(attrs: List<RtmAttribute>): Map<String, *> {
    return attrs.map {
      it.key to it.value
    }.toMap()
  }
}
