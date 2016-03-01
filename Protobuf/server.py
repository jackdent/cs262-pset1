# ********************************************
# TODO : What do we do if a user sends a message, then deletes themself?
#        (the unreceived message has a ref to a deleted user)
# ********************************************

from flask import Flask
from build import request_pb2 as RequestProtoBuf
from model import User, UserList, GroupList

app = Flask(__name__)

USERS = UserList()
GROUPS = GroupList()

#
# Users
#

@app.route("/users", methods=["GET"])
def listUsers():
    return USERS.serialize()

@app.route("/users/<username>", methods=["POST"])
def createUser(username):
    if USERS.usernameExists(username):
        raise UserError("User Exists")

    user = User(username)
    USERS.addUser(user)
    return user.serialize()

@app.route("/users/<username>", methods=["DELETE"])
def deleteUser(username):
    if not USERS.usernameExists(username):
        raise UserError("Missing User")

    USERS.deleteUser(username)
    GROUPS.pruneUser(username)

#
# Groups
#

@app.route("/groups", methods=["GET"])
def listGroups():
    return GROUPS.serialize()

@app.route("/groups/<groupname>", methods=["POST"])
def createGroup(groupname):
    if GROUPS.groupnameExists(groupname):
        raise UserError("Group Exists")

    group = Group(groupname)
    GROUPS.addGroup(group)
    return group.serialize()

@app.route("/groups/<groupname>/users/<username>", methods=["PUT"])
def addUserToGroup(groupname, username):
    group = GROUPS.getGroup(groupname)
    if group is None:
        raise UserError("Missing Group")

    if not USERS.usernameExists(username):
        raise UserError("User does not exist")

    group.addUser(username)
    return group.serialize()

#
# Messages
#

def decodeMessage(request):
    # TODO: fix this
    message = RequestProtoBuf.Message.unwrap(request.body)

    if message.msg is None or msg == '':
        raise UserError("Invaid Message Body")

    fromUser = USERS.getUser(message.frm)
    if fromUser is None:
        raise UserError("Invaid from User")

    return fromUser, msg

@app.route("/users/<username>/messages", methods=["POST"])
def sendDirectMessage(username):
    msg, fromUser = decodeMessage(request)
    toUser = USERS.getUser(username)
    if toUser is None:
        raise UserError("Missing To User")

    message = DirectMessage(fromUser, toUser, msg)
    toUser.receiveMessage(message)
    return True

@app.route("/groups/<groupname>/messages", methods=["POST"])
def sendGroupMessage(groupname):
    msg, fromUser = decodeMessage(request)
    toGroup = GROUPS.getGroup(groupname)
    if toGroup is None:
        raise UserError("Missing To Group")

    message = GroupMessage(fromUser, toGroup, msg)
    toGroup.receiveMessage(message, USERS)
    return True

@app.route("/users/<username>/messages", methods=["GET"])
def listMessages(username):
    user = USERS.getUser(username)
    if user is None:
        raise UserError("Missing User")

    return user.flushMessages()

if __name__ == "__main__":
    # TODO : Not sure we should keep debug in 'prod'
    app.run(debug=True)
