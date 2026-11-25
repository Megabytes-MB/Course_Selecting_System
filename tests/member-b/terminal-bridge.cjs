// Evidence helper only: a loopback terminal connected to the real Java Main process.
// It never fabricates program output or executes arbitrary shell commands.
const http = require('node:http');
const { spawn } = require('node:child_process');
const path = require('node:path');
const java = process.argv[2];
const config = process.argv[3];
if (!java || !config) throw new Error('Usage: node terminal-bridge.cjs JAVA_EXE PRIVATE_CONFIG');
const root = path.resolve(__dirname, '../..');
const classpath = [path.join(root, 'app/target/classes'), path.join(root, 'app/target/dependency/*')].join(path.delimiter);
let child;
let output = '';
let active = false;
const page = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>JDBC 实时运行验证</title>
<style>body{margin:24px;background:#f4f6f8;color:#18232e;font-family:Arial,"Microsoft YaHei",sans-serif}h1{font-size:22px;margin:0 0 8px}p{font-size:14px}pre{background:#111923;color:#edf2f7;padding:20px;font:15px/1.65 Consolas,"Microsoft YaHei",monospace;white-space:pre-wrap;overflow-wrap:anywhere;border-radius:6px}textarea{width:60%;height:80px}button{padding:10px;margin:8px}#state{font-weight:bold}</style>
<h1>成员 B JDBC 命令行实时验证</h1><p>直接连接正式 edu.project.Main 的标准输入输出；MySQL 8.4.7 / JDK 17.0.18。教学班使用 B 测试夹具。</p>
<button id="start">启动正式 Main</button><span id="state">未启动</span><pre id="terminal"></pre>
<label for="input">输入菜单编号与参数，每行一项</label><br><textarea id="input"></textarea><button id="send">发送输入</button>
<script>
async function request(url,body){const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});if(!r.ok)throw Error(await r.text());}
document.querySelector('#start').onclick=()=>request('/start',{});
document.querySelector('#send').onclick=async()=>{const box=document.querySelector('#input');await request('/input',{text:box.value});box.value='';};
setInterval(async()=>{const r=await fetch('/output');const data=await r.json();document.querySelector('#terminal').textContent=data.output;document.querySelector('#state').textContent=data.active?'Java 进程运行中':'Java 进程已结束';},150);
</script></html>`;
const server = http.createServer(async (req, res) => {
  const origin = 'http://127.0.0.1:33085';
  res.setHeader('Cache-Control', 'no-store');
  if (req.headers.host !== '127.0.0.1:33085' || (req.headers.origin && req.headers.origin !== origin)) {
    res.writeHead(403).end(); return;
  }
  if (req.method === 'GET' && req.url === '/') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(page); return; }
  if (req.method === 'GET' && req.url === '/output') { res.setHeader('Content-Type', 'application/json; charset=utf-8'); res.end(JSON.stringify({ output, active })); return; }
  if (req.method !== 'POST' || req.headers['content-type'] !== 'application/json') { res.writeHead(400).end(); return; }
  let body = '';
  for await (const chunk of req) { body += chunk; if (body.length > 16384) { res.writeHead(413).end(); return; } }
  if (req.url === '/start') {
    if (active) { res.writeHead(409).end('Process already running'); return; }
    output = ''; active = true;
    child = spawn(java, ['-Dfile.encoding=UTF-8', '-Ddb.config=' + config, '-cp', classpath, 'edu.project.Main'], { cwd: root, windowsHide: true });
    const current = child;
    for (const stream of [current.stdout, current.stderr]) {
      stream.setEncoding('utf8'); stream.on('data', text => { output += text; if (output.length > 1000000) current.kill(); });
    }
    current.on('exit', code => { active = false; output += '\n[Java process exit=' + code + ']\n'; });
    current.on('error', () => { active = false; output += '\n[Java process could not start]\n'; });
    res.end('{}'); return;
  }
  if (req.url === '/input' && active) {
    try {
      const data = JSON.parse(body);
      if (typeof data.text !== 'string') throw new Error();
      output += '\n[stdin]\n' + data.text + '\n[/stdin]\n';
      child.stdin.write(data.text + '\n'); res.end('{}');
    } catch { res.writeHead(400).end(); }
    return;
  }
  res.writeHead(404).end();
});
server.listen(33085, '127.0.0.1', () => console.log('Real Java terminal bridge listening on loopback port 33085'));
function stop() { if (child && active) child.kill(); server.close(() => process.exit()); }
process.on('SIGINT', stop); process.on('SIGTERM', stop);
