#!/usr/bin/env python3
import http.server
import socketserver
import json
import sys

PORT = 28080

class MockLLMHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        print("Received request data:", post_data.decode('utf-8'))
        
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        
        response = {
            "choices": [
                {
                    "message": {
                        "content": "🤖 [MOCK DIAGNOSIS] The task failed because it attempted to read 'nonexistent_file.txt', which does not exist in the working directory."
                    }
                }
            ]
        }
        self.wfile.write(json.dumps(response).encode('utf-8'))

    def log_message(self, format, *args):
        # Suppress standard logging to keep test output clean
        return

if __name__ == "__main__":
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("", PORT), MockLLMHandler) as httpd:
        print(f"Mock LLM server running on port {PORT}")
        sys.stdout.flush()
        httpd.serve_forever()
