#!/usr/bin/env python
import web
import json
import uuid
import time
import hashlib
import os
import json

if __name__ != "__main__":              # mod_wsgi has no concept of where it is
    os.chdir(os.path.dirname(__file__)) # any relative paths will fail without this

# Exposed urls, and their respective class.
#
# Examples/usage:
#
#    http://localhost:8080/getToken/<username>
#    http://localhost:8080/openDoor/<token_id>/<md5_generated_hash>
#    http://localhost:8080/getDoorStatus
#
urls = (
    '/getToken/(.*)', 'getToken',
    '/openDoor/(.*)/(.*)', 'openDoor',
    '/getTokens', 'getTokens',
    '/getDoorStatus', 'getDoorStatus'
)

# Application object, give it our list of urls and globals() for class lookup.
app = web.application(urls, globals())


# A dictionary of users and their password
USERS = { 'andrew': "blah123",
          'kim': "dude123" }

################
# getToken class
#
# Class used for our initiation our authorization. This class returns a token,
# and the token id if a valid user us found.
#
# Returned json values
#    Standard:
#        return_type: The type of return we're sending. In this case it will be 0
#                     due to this being the getToken stage.
#
#        return value: The value of this is whether getToken succeeded. Anything
#                      non-zero is a failure. So far, the only thing that throws
#                      a failure, is the user missing.
#
#        user: The user that has been requested, this value is used from what
#              the client has sent, not from the list of users.
#
#    Optional:
#        token_id: This is the token id for the current session. Since we dont
#                  use a persistent connection, this allows us to look up the
#                  token previously calculated.
#
#        token: This is a 32 bit randomly generated token. The client uses this
#               to hash the password. This is also used to verify the hash
#               is correct.
#
###############################################################################
class getToken:
    def GET(self, user):
        # make sure the user actually exists
        if user in USERS:
            token = uuid.uuid4().hex
            token_id = int(time.time())

            data = { 'return_type': 0,
                     'return_value': 0,
                     'user': user,
                     'token_id': token_id,
                     'token': token }

            entries = []

            try:
                with open("tokens.lst", "r") as tokenFile:
                    entries = json.load(tokenFile)
            except IOError:
                pass
            except ValueError:
                pass

            entries.append(data)

            with open("tokens.lst", "w") as tokenFile:
                tokenFile.write(json.dumps(entries))
        else:
            data = { 'return_type': 0,
                     'return_value': 1,
                     'user': user}

        return json.dumps(data)

# I wrote this as more of a debug function. I don't see a need for it anymore.
# This will be transformed into a "clearTokens" function. Which will clear the
# list of tokens that have been generated. It was serve as a troubleshooting
# function.
class getTokens:
    def GET(self):
        content = []
        try:
            with open("tokens.lst", "r") as tokenFile:
                content = json.load(tokenFile)
        except IOError:
            pass
        except ValueError:
            pass

        if len(content) > 0:
            return json.dumps(content)
        else:
            return "Error: No tokens found!"

# What this function needs to do is get the status of N gpio pin.
# If grounded, door is closed. If voltage is seen, door is open.
# ( IIRC this is what we'll be seeing ).
class getDoorStatus:
    def GET(self):

        door_status = -1
        try:
            with open("door_status", "r") as tokenFile:
                door_status = tokenFile.read()
        except IOError:
            pass

        data = { 'return_type': 3,
                 'return_value': door_status }

        return data


class openDoor:
    def GET(self, token_id, hash):

        with open("tokens.lst", "r") as tokenFile:
            entries = json.load(tokenFile)

        for entry in entries:
            if str(entry['token_id']) == token_id:

                password = USERS['andrew']

                m = hashlib.md5()
                m.update(password + entry['token'])
                md5 = m.hexdigest()

                if md5 == hash:
                    data = { 'return_type': 1,
                             'return_value': 0,
                             'return_message': "" }

                    return data
                else:
                    data = { 'return_type': 1,
                             'return_value': 1,
                             'return_message': "Incorrect hash" }
                    return data

        data = { 'return_type': 1,
                 'return_value': -1,
                 'return_message': "Something went wrong." }

        return data

if __name__ == "__main__":
    app.run() # Used when executing the script manually. Allows for debugging.
else:
    application = app.wsgifunc() # Used when apache and wsgi execute the script.
