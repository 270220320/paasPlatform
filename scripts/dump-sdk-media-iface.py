#!/usr/bin/env python3
import zipfile, re
z = zipfile.ZipFile('/mnt/d/AiFlow/backend/libs/flight-hub-sdk.jar')
src = z.read('com/dji/flighthubsdk/generated/IDeviceDebugModule.java').decode('utf-8', 'ignore')
methods = re.findall(r'public\s+void\s+(\w+)\(', src)
print('total:', len(methods))
for m in methods:
    if re.search(r'reboot|restart|debug|dock|airport|cover|putter|charge|calibrate|drone', m, re.I):
        print(m)
