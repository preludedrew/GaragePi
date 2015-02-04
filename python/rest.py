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

# Define exposed URL's
urls = (
    '/getToken/(.*)', 'getToken',
    '/openDoor/(.*)/(.*)', 'openDoor',
    '/getTokens', 'getTokens',
    '/getDoorStatus', 'getDoorStatus'
)

app = web.application(urls, globals())

USERS = { 'andrew': "blah123",
          'kim': "dude123" }

class getToken:
    def GET(self, user):

        if user in USERS:
            token = uuid.uuid4().hex
            token_id = int(time.time())

            data = { 'return_type': 0,
                     'return_value': 0,
                     'token_id': token_id,
                     'user': user,
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

class getDoorStatus:
    def GET(self):

        # What this function needs to do is get the status of N gpio pin. If grounded, door is closed. If voltage is seen, door is open. ( IIRC this is what we'll be seeing ).

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
    app.run() # devel
else:
    application = app.wsgifunc() # apache2 + wsgi
