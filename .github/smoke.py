"""Ждёт запуска сервера, шлёт команды по RCON и пишет ответы в smoke_result.txt."""
import socket, struct, time, re, sys
def wait_log(pat, timeout):
    t0 = time.time()
    while time.time() - t0 < timeout:
        try:
            if re.search(pat, open('server.log', errors='ignore').read()): return True
        except FileNotFoundError: pass
        time.sleep(3)
    return False
class Rcon:
    def __init__(s):
        s.s = socket.create_connection(('127.0.0.1', 25575), timeout=120); s.i = 0
        s.cmd('smoke', 3)
    def pkt(s, t, body):
        s.i += 1
        d = struct.pack('<ii', s.i, t) + body.encode() + b'\0\0'
        s.s.sendall(struct.pack('<i', len(d)) + d)
        ln = struct.unpack('<i', s.recv(4))[0]
        data = s.recv(ln)
        return data[8:-2].decode(errors='ignore')
    def recv(s, n):
        b = b''
        while len(b) < n: b += s.s.recv(n - len(b))
        return b
    def cmd(s, c, t=2): return s.pkt(t, c)
out = open('smoke_result.txt', 'w')
ok = wait_log(r'Done \(', 1500)
out.write(f'started: {ok}\n'); out.flush()
if not ok: sys.exit(0)
r = Rcon()
cmds = [
  'execute in vestments:silent_cathedral run forceload add 0 0',
  'execute in vestments:silent_cathedral run forceload add 336 -16 352 16',
  'WAIT 15',
  'execute in vestments:silent_cathedral if block 344 -58 0 vestments:bell_portal run say PORTAL_OK',
  'execute in vestments:silent_cathedral if block 344 -59 -2 minecraft:oxidized_cut_copper run say FRAME_OK',
  'execute in vestments:silent_cathedral if block 0 -56 0 minecraft:air run say CENTER_AIR',
  'execute in vestments:silent_cathedral run data get block 326 -60 4 Command',
  'execute in vestments:silent_cathedral if entity @e[type=vestments:bell_ringer] run say RINGER_OK',
  'execute in vestments:silent_cathedral unless block 0 -95 0 minecraft:air run say UNDERSIDE_OK',
  'execute in vestments:silent_cathedral if block 0 -56 0 minecraft:air run say FLOOR_LEVEL_OK',
  'execute in vestments:silent_cathedral run data get entity @e[type=vestments:bell_ringer,limit=1] Dormant',
  'locate structure vestments:bell_courtyard',
  'forceload add 0 0 127 127',
  'WAIT 25',
  'place template vestments:bell_courtyard 0 120 0',
  'execute if block 61 123 45 minecraft:oxidized_cut_copper run say TEMPLATE_FRAME_OK',
  'execute if block 61 123 45 minecraft:weathered_cut_copper run say TEMPLATE_FRAME_OK2',
  'data get block 61 123 45',
  'forceload add 112 -352 240 -224',
  'WAIT 40',
  'execute in vestments:silent_cathedral run summon vestments:silent_bell 10 -56 10',
  'WAIT 5',
  'execute in vestments:silent_cathedral run kill @e[type=vestments:silent_bell]',
  'stop',
]
for c in cmds:
    if c.startswith('WAIT'):
        time.sleep(int(c.split()[1])); continue
    try:
        res = r.cmd(c)
    except Exception as e:
        res = f'ERR {e}'
    out.write(f'> {c}\n{res}\n'); out.flush()
    time.sleep(1)
time.sleep(20)
