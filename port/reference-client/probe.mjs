import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import assert from 'node:assert/strict';

const token=process.env.JCLAW_PORT_READ_TOKEN;
assert(token?.length>=32,'Set JCLAW_PORT_READ_TOKEN for the running local bridge');
const base=new URL(process.env.JCLAW_PORT_PROBE_URL??'http://127.0.0.1:8087/mcp');
const client=new Client({name:'jclaw-reference-probe',version:'1.0.0'});
const transport=new StreamableHTTPClientTransport(base,{
  requestInit:{headers:{Authorization:`Bearer ${token}`}}
});
const deadline=setTimeout(()=>{console.error('MCP probe timed out');process.exit(1)},15_000);
let closing=false;
client.onerror=error=>{if(!closing)console.error('MCP protocol error:',error.message)};
try {
  await client.connect(transport);
  const listed=await client.listTools();
  assert.deepEqual(listed.tools.map(t=>t.name).sort(),['getCalendar','getOrganizerSensitivity']);
  for(const [name,args] of [['getCalendar',{}],['getOrganizerSensitivity',{name:'Dana from People Ops'}]]) {
    const result=await client.callTool({name,arguments:args});
    assert(!result.isError&&result.content.some(c=>c.type==='text'&&c.text.length>0));
  }
  console.log('PASS: reference SDK handshake and both read tools; no write tool exposed');
} finally {
  closing=true;
  clearTimeout(deadline);
  await client.close();
}
