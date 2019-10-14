# tx-nodes

## Run

It is possible to run the project independently of the IDE you run it on using gradle with: 

```bash
gradle run
```

## Project structure

```
|_ models
    |_ Message
    |_ NodeReference
|_ Main
|_ MasterNode
|_ Node
```

### Node 

The Node class implements all of our nodes common functionalities s.as. a tcp Server in order to communicate with its peers etc.
It holds a ref to the master node which allows it to be integrated in the network by sending a `connection request` to the MN.
We handle received messages in a `connection handler` which reads the message of the connection `socket` and, givent its type deals with it in different manners (cf. `NodeClass.connectionHandler.handleMessage()`)

### MasterNode (MN)

The MasterNode class extends Node and adds functions specific to the MN s.as. a http server in order to receive requests from clients

## Models

### Message

A message is a serializable object that can therefore be transmitted via socket streams. It holds:

- `senderReference`: a NodeReference of the sender
- `type`: a String describing the intent of the message
- `data`: an object of any type that holds a serializable object to be transmitted

### NodeReference

It contains an IP/Port able to uniquely identify each node. We can also use those IP/Port to pass it to the send method from the node.

## Connexion between nodes

Each nodes has a number of child given by `maxOfChild` a field specified in its class. And is connected to some brothers i.e. its parents children. We have a tree connection.

```
            MN                      <- Master Node, here the parent node
____________|____________________
|       |       |       |       |
N ----- N ----- N ----- N ----- N   <- child nodes where Numof(N) < maxOfChild
...    ...     ...     ...     ... 
```

When a new node wants to join the network, he sends a `connect` msg to the master node. Then, as each node does when they receive a connection request, he verifies that he can add the node in his children (i.e. `children.size < maxOfChild`)
 - if yes, then we add him and send his reference to all of our children so they can add him to their brothers reference. Then we send all of our children to the node that just joined so he can add them as his brothers
 - if no, we transfer the connection request to one of our child that will handle it. This process will recursively happen until a node can hold the new ref as one of his children

### Crash detection

Each nodes has a `rate` field a Int that represents the number of milliseconds the idle checker in each node will wait between two spree.

For each spree, a node checks that all of his children are still active and removes the one that are not. And then checks if his parent is active. If it is not, then he resend a connection request to the MasterNode and will be placed elsewhere with a working parent.

It is IMPORTANT to notice that ALL the children will be relocated with us. A whole portion of the tree is then moved to somewhere else. The tree is rarely perfectly shaped but it doesn't matter especially as time goes and more and more Nodes becomes MN.