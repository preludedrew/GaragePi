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
    '/getTokens', 'getTokens'
)

app = web.application(urls, globals())

USERS = { 'andrew': "blah123",
          'kim': "dude123" }

class getToken:
    def GET(self, user):
        token = uuid.uuid4().hex
        token_id = int(time.time())

        data = { 'token_id': token_id,
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
                    return "Correct! -- You may proceed"
                else:
                    return "Error: Incorrect hash [ %s ]" % hash

        return "Error: Something went wrong."

if __name__ == "__main__":
    app.run() # devel
else:
    application = app.wsgifunc() # apache2 + wsgi
