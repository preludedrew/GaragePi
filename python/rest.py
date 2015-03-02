#!/usr/bin/env python
import web
import json
import uuid
import time
import hashlib
import os
import json
import pigpio
import urlparse

DEBUG = False

RETURN_TYPE_TOKEN = 0
RETURN_TYPE_OPEN_DOOR = 1
RETURN_TYPE_LOGS = 2 #Unimplemented
RETURN_TYPE_DOOR_STATUS = 3

RETURN_VAL_SUCCESS = 0
RETURN_VAL_FAIL = 1
RETURN_VAL_UNKNOWN = -1

if not DEBUG:
    pi = pigpio.pi()

    GPIO_TOGGLE = 4
    GPIO_READ = 25

    pi.set_mode(GPIO_TOGGLE, pigpio.OUTPUT)
    pi.set_mode(GPIO_READ, pigpio.INPUT)
    pi.set_pull_up_down(GPIO_READ, pigpio.PUD_UP)

if __name__ != "__main__":              # mod_wsgi has no concept of where it is
    os.chdir(os.path.dirname(__file__)) # any relative paths will fail without this

# Exposed urls, and their respective class.
#
# Examples/usage:
#
#    http://localhost:8080/getToken?username=<username>
#    http://localhost:8080/openDoor?token_id=<token_id>&hash=<md5_generated_hash>&debug=<optional true/false>
#    http://localhost:8080/getDoorStatus
#
urls = (
    '/getToken', 'getToken',
    '/openDoor', 'openDoor',
    '/getDoorStatus', 'getDoorStatus',
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
    def GET(self):

        user = web.input(user="")['user']

        # make sure the user actually exists
        if user in USERS:
            token = uuid.uuid4().hex
            token_id = int(time.time())

            data = { 'return_type': RETURN_TYPE_TOKEN,
                     'return_value': RETURN_VAL_SUCCESS,
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
            data = { 'return_type': RETURN_TYPE_TOKEN,
                     'return_value': RETURN_VAL_FAIL,
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

        if not DEBUG:
            door_status = int(pi.read(GPIO_READ))
        else:
            door_status = -1

        data = { 'return_type': RETURN_TYPE_DOOR_STATUS,
                 'return_value': door_status }

        return data


class openDoor:
    global DEBUG

    def GET(self):

        token_id = web.input(token_id="")['token_id']
        hash = web.input(hash="")['hash']
        DEBUG = (web.input(debug="false")['debug'].lower() in "true")

        with open("tokens.lst", "r") as tokenFile:
            entries = json.load(tokenFile)

        for entry in entries:
            if str(entry['token_id']) == token_id:

                password = USERS[entry['user']]

                m = hashlib.md5()
                m.update(password + entry['token'])
                md5 = m.hexdigest()

                if md5 == hash:
                    data = { 'return_type': RETURN_TYPE_OPEN_DOOR,
                             'return_value': RETURN_VAL_SUCCESS,
                             'return_message': "" }

                    if not DEBUG:
                        pi.write(4, 0)
                        time.sleep(.1)
                        pi.write(4, 1)
                    return data
                else:
                    data = { 'return_type': RETURN_TYPE_OPEN_DOOR,
                             'return_value': RETURN_VAL_FAIL,
                             'return_message': "Incorrect hash" }
                    return data

        data = { 'return_type': RETURN_TYPE_OPEN_DOOR,
                 'return_value': RETURN_VAL_UNKNOWN,
                 'return_message': "Something went wrong." }

        return data

if __name__ == "__main__":
    app.run() # Used when executing the script manually. Allows for debugging.
else:
    application = app.wsgifunc() # Used when apache and wsgi execute the script.
