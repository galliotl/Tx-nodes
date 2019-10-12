package tx.nodes

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tx.nodes.models.Message
import tx.nodes.models.NodeReference
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.math.floor

open class Node(protected val port: Int, protected val ip: String = "localhost") {
    //________________________________FIELDS
    // Node detail
    protected var ownReference: NodeReference = NodeReference(ip, port)

    // Node activity
    protected var active = true
    protected val server = ServerSocket(port)

    // IP tree structure
    val masterNodeReference = NodeReference("localhost", 7777)
    val maxOfChild = 5
    var parent:NodeReference = masterNodeReference
    var children:MutableList<NodeReference> = mutableListOf()
    var brothers:MutableList<NodeReference> = mutableListOf()


    //________________________________METHODS
    open fun run() {
        thread { tcpServer() }
        // we send a connection request to the master Node
        send(masterNodeReference, Message(senderReference = ownReference, type = "connect"))
        thread { idleCheck(10000) }
    }
    open fun shutdown() {
        active = false
        server.close()
    }
    /**
     * Is used to log a message, specifying the node who sent it
     */
    fun log(msg: String) {
        // atm we just print it
        println("$ownReference: $msg")
    }
    /**
     * TCP server that listens on a given port and creates a coroutine to handle
     * every connections
     */
    protected fun tcpServer() = runBlocking{
        active = true
        while (active) {
            val node = server.accept()
            GlobalScope.launch { connectionHandler(node) }
        }
    }
    fun connectionHandler(socket: Socket) {
        try {
            val oos = ObjectOutputStream(socket.getOutputStream())
            val ois = ObjectInputStream(socket.getInputStream())

            // Def of inner functions
            fun addInChildren(node: NodeReference): Boolean {
                if(children.size < maxOfChild) {
                    // we send all our children to the new child -> his brothers
                    send(node, Message(type = "connect confrim", data = children, senderReference = ownReference))

                    // we send a reference of the node to all of our children so they can populate their neighbour
                    for(child in children) {
                        try {
                            send(child, Message(senderReference = ownReference, type = "add brother", data = node))
                        } catch (ex: Exception) {
                            log("couldn't send the brother reference to $child")
                        }
                    }
                    // then we add him
                    children.add(node)
                    return true
                }
                return false
            }
            fun getRandomChild(): NodeReference? {
                return children[floor(Math.random() * children.size).toInt()]
            }
            fun handleMessage(msg: Message) {
                when (msg.type) {
                    /**
                     * Connect is the first msg a node sends. It contains its details.
                     * We check if we can still host a child and if yes everything is cool.
                     * If not, we transfer the responsibility to one of our children.
                     *
                     * The only way addInChildren returns false is if there's more than the accepted limit
                     * of children already. Contrary to getRandomChild who returns null when there's exactly 0 children in the list
                     * The 2 functions are therefore on opposition and getRandomChild cannot return a null value in this case.
                     */
                    "connect" -> {
                        if(addInChildren(msg.senderReference)) {
                            log("my children: ${children.toString()}")
                        } else {
                            // We send a msg to our child
                            val child = getRandomChild()
                            log("passing responsibility of ${msg.senderReference} to $child as my list is full")
                            // is always true, but prevent compilation errors
                            if (child != null) {
                                // we send the exact same msg to our child
                                send(child, msg)
                            }
                        }
                    }
                    /**
                     * connect confirm means that the node was accepted by a parent node
                     * which reference can be found in the msg sent
                     * We get the brothers and update our parent
                     */
                    "connect confirm" -> {
                        parent = msg.senderReference
                        log("my parent node is $parent")
                        if(msg.data is MutableList<*> && msg.data.size > 0 && msg.data[0] is NodeReference) {
                            brothers = msg.data as MutableList<NodeReference>
                            log("my brothers: ${brothers.toString()}")
                        }
                    }
                    "add brother" -> {
                        if(msg.data is NodeReference) {
                            brothers.add(msg.data)
                            log("my brothers: ${brothers.toString()}")
                        }
                    }
                    "crash report" -> {
                        // We verify that we
                    }
                    else -> {
                        log("${msg.type} not known, connection will shutdown")
                    }
                }
            }
            try {
                val msg = ois.readObject() as Message
                handleMessage(msg)
                // connection is always closed after msg is dealt with
                oos.close()
                ois.close()
                socket.close()
            } catch (e: Exception) {
                log("couldn't handle the message")
                e.printStackTrace()
                oos.close()
                ois.close()
                socket.close()
            }
        }
        catch (ioe: IOException) {
            // Was just being checked by the parent, a connection was closed before I could read the streams
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun send(node: NodeReference, msg: Message) {
        try {
            val socket = Socket(node.ip, node.port)
            val os = ObjectOutputStream(socket.getOutputStream())
            os.writeObject(msg)
        } catch(ioe: IOException) {
            log("connection was closed by remote host -> received")
        } catch(e: Exception) {
            log("couldn't send the message")
            e.printStackTrace()
        }
    }

    /**
     * Idle check verifies that our parent is still active
     */
    fun idleCheck(rate: Long) {
        /**
         * Inner function, checks if a certain ip port node is reachable. It's a homemade solution as it will use Messages
         */
        fun isRemoteNodeReachable(node: NodeReference): Boolean {
            var toReturn = false
            try {
                // If connection is accepted then it means the node is active
                val socket = Socket(node.ip, node.port)
                socket.close()
                toReturn = true
            } catch (e: Exception) { toReturn = false }
            return toReturn
        }
        while (active) {
            Thread.sleep(rate)
            if(!isRemoteNodeReachable(parent)) {
                // We contact master node to notify. The rest will be taken care of when we receive the answer
                send(masterNodeReference, Message(type = "crash report", senderReference = ownReference, data = parent))
            }
        }
    }
}

fun main(args: Array<String>) {
    val port = args[0].toInt()
    val node = Node(port) // to change each time
    node.run()
}