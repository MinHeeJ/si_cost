"""Local-only SMTP collector, no forwarding; Python standard library only."""
import socketserver,http.server,threading,json
MESSAGES=[]
class SMTP(socketserver.StreamRequestHandler):
 def handle(self):
  self.wfile.write(b'220 localhost collector\r\n');lines=None
  for raw in self.rfile:
   line=raw.rstrip(b'\r\n')
   if lines is not None:
    if line==b'.':MESSAGES.append(b'\n'.join(lines).decode('utf-8','replace'));lines=None;self.wfile.write(b'250 collected\r\n')
    else:lines.append(line)
   elif line.upper().startswith(b'DATA'):lines=[];self.wfile.write(b'354 send data\r\n')
   elif line.upper().startswith(b'QUIT'):self.wfile.write(b'221 bye\r\n');return
   else:self.wfile.write(b'250 OK\r\n')
class HTTP(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  out=json.dumps({'count':len(MESSAGES),'messages':MESSAGES}).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(out)
 def log_message(self,*args):pass
if __name__=='__main__':
 smtp=socketserver.ThreadingTCPServer(('127.0.0.1',1025),SMTP);threading.Thread(target=smtp.serve_forever,daemon=True).start()
 print('SMTP 127.0.0.1:1025 / messages http://127.0.0.1:8025',flush=True)
 http.server.ThreadingHTTPServer(('127.0.0.1',8025),HTTP).serve_forever()
