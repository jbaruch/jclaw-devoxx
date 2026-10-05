#!/usr/bin/env python3
"""Build reviewable Port setup artifacts locally; never authenticate or deploy."""
import argparse
import hashlib
import json
import pathlib
import re
from urllib.parse import urlparse

ROOT = pathlib.Path(__file__).resolve().parent.parent
PORT = ROOT / 'port'
MAX_REFINEMENTS = json.loads((ROOT/'domain/src/main/resources/jclaw/workflow/policy.json').read_text())['maxRefinements']
FLAVORS = ['CALENDAR_CONFLICT', 'FAMILY_OBLIGATION', 'CUSTOMER_ESCALATION', 'DEADLINE', 'ALREADY_PROFICIENT', 'EXISTENTIAL_CRISIS']
TIERS = ['AIRTIGHT', 'CREDIBLE', 'THIN', 'HR_WILL_NOTICE']
REQUEST = "Get me out of the Basic AI Proficiency Training on Tuesday, run by Dana from People Ops. Don't reuse an excuse I've already used on her - tell me which ones you're avoiding."
AGENT_IDS = {'entry':'jclaw','identify':'jclaw-identify','draft':'jclaw-draft-refine','judge':'jclaw-judge'}
READ_TOOLS = ['jclaw-read_getCalendar','jclaw-read_getOrganizerSensitivity']
SYSTEM_PROMPT = (
    'You are j-claw. Use supplied facts and actual read-tool results to return the requested typed result. '
    'Read tools and loading skills are permitted. Execute every requested read or skill tool before answering. '
    'search_tools returns tool definitions, not evidence: after discovery, invoke the discovered tool by name. '
    'Never invoke delivery or any write tool.')

def obj(properties, required=None):
    return {'type':'object', 'properties':properties, 'required':required or list(properties), 'additionalProperties':False}

STR = {'type':'string'}
FLAVOR = {'type':'string','enum':FLAVORS}
ARRAY_FLAVOR = {'type':'array','items':FLAVOR}
REQUEST_SCHEMA = obj({'eventId':dict(STR,minLength=1),'recentlyUsedFlavors':ARRAY_FLAVOR,'knownAttendees':{'type':'array','items':STR},
    'organizerName':dict(STR,minLength=1),'userInstruction':STR,'previouslyProposedFlavors':ARRAY_FLAVOR})
PLAN_SCHEMA = obj({'flavor':FLAVOR,'fakeCalendarEventId':{'type':['string','null']},'messageToOrganizer':STR,'hallwayScript':STR})
CRITIQUE_SCHEMA = obj({'tier':{'type':'string','enum':TIERS},'approved':{'type':'boolean'},'feedback':STR})

def fixture():
    source = (ROOT/'mocks/src/main/kotlin/jclaw/mocks/Store.kt').read_text()
    events=[]
    for block in re.findall(r'CalendarEvent\(\s*id\s*=\s*"[^"]+".*?\n\s*\)',source,re.S):
        fields=dict(re.findall(r'(id|title|start|organizer)\s*=\s*"([^"]*)"',block))
        if len(fields)!=4: raise ValueError('Calendar fixture shape changed; inspect Store.kt')
        events.append(dict(fields,declined='declined = true' in block))
    assert len(events)==4
    target=events[0]
    scenario=(ROOT/'domain/src/main/kotlin/jclaw/domain/Scenario.kt').read_text()
    profile=''.join(re.findall(r'"([^"]*)"',scenario.split('public const val USER_CONTEXT: String =',1)[1]))
    records=[]
    for name in ('2e3eea73-6e25-5ab8-8159-d986db9e0f38','2e72c8ef-dd58-524c-adb5-4e0c39e9a865','87ac1ec3-7e7f-56bd-a18f-3e731e57a676'):
        text=(ROOT/'memory/documents'/name).read_text().strip()
        date=text.split(':',1)[0]
        event=next(e for e in events if e['start'].startswith(date))
        flavor=re.search(r'Excuse flavor used: ([A-Z_]+)',text).group(1)
        records.append({'identifier':'jclaw-seed-'+name,'title':event['title']+' — sent excuse',
            'properties':{'eventId':event['id'],'organizerName':event['organizer'],'flavor':flavor,
                'message':text,'deliveredAt':event['start'],'source':'Fictional shared seed history'},
            'relations':{'event':event['id']}})
    return events,target,profile,records

def blueprint(identifier,title,properties,relations=None,required=None):
    return {'identifier':identifier,'title':title,'icon':'Code',
        'schema':{'properties':properties,'required':required or list(properties)},'relations':relations or {},
        'mirrorProperties':{},'calculationProperties':{},'aggregationProperties':{}}

def template(expression): return '{{ '+expression+' }}'
def output(name,field='response.data'): return f'.outputs.{name}.{field}'

def build_agents(config):
    roles = [
        ('entry', 'j-claw',
         'Launch the reviewed decline workflow when the user asks to get out of, skip or decline '
         'a training session or workshop. First CALL list_self_service_triggers to discover '
         'jclaw_decline and its trigger/input schema. Then CALL trigger_run exactly once with '
         'type=WORKFLOW, the discovered workflowIdentifier and nodeIdentifier, '
         'inputs.request containing the user\'s complete request verbatim, and executionMode=automatic. '
         'Do not just provide a self-service form link or suggest a draft. Do not identify the event, '
         'draft, judge, send or write history yourself: the workflow owns those steps. '
         'Return the actual run link from the triggered run ID. You may read its status once, '
         'but do not repeatedly poll or wait for it to finish. Human review takes place in the '
         'workflow run page. Never answer the human INPUT or approve on the user\'s behalf. '
         'Do not launch another run for feedback on an existing run; direct the user to its '
         'human review. Only report delivery when the run proves a confirmed receipt.',
         ['^list_self_service_triggers$', '^trigger_run$', '^get_(run|workflow_run)$'], REQUEST),
        ('identify', 'j-claw · Identify',
         'Find the obligation and exact organizer using the read-only calendar and organizer MCP tools. '
         'First CALL jclaw-read_getCalendar, then CALL jclaw-read_getOrganizerSensitivity with the exact '
         'organizer from that result. Copy the returned event ID verbatim. Never invent an ID or finalize '
         'after only search_tools: tool definitions are not calendar evidence. Both actual reads are '
         'mandatory. The workflow canonicalizes the request and adds confirmed sent history.', READ_TOOLS,
         'Identify the Basic AI Proficiency Training and its organizer from the calendar.'),
        ('draft', 'j-claw · Draft & Refine',
         'Draft or refine a candidate from the supplied typed request, user context and critic feedback. '
         'Preserve the event and organizer, honor human feedback, and avoid both sent and proposed flavors. '
         'Use concise, reasonably plausible text and fix concrete feedback with the smallest change. '
         'Ordinary preparation for the supplied public AI-agent talk needs no independent proof. '
         'Ask for an exception; do not assume permission. The organizer email and hallway script are literal outbound text: keep '
         'internal notes and avoided-excuse explanations out of both. Set fakeCalendarEventId=null. '
         'The workflow owns both critics, the shared six-refinement budget and human approval.', ['^$'],
         'Draft a decline from this request and user context; return the complete candidate.'),
        ('judge', 'j-claw · Judge',
         'Load jclaw-decline-review and judge the exact candidate against the current request, human '
         'feedback and avoided-flavor lists. Return the requested typed verdict. An honest first draft '
         'may pass. This is critic one; the human is critic two. Approval grants no permission to send.', ['load_skill'],
         'Review this current request and candidate using jclaw-decline-review.'),
    ]
    agents=[]
    for role,title,purpose,tools,starter in roles:
        prompt=purpose if role=='entry' else SYSTEM_PROMPT+'\n'+purpose
        if role=='entry':
            org_path=config.get('expectedOrgId')+'/' if config.get('expectedOrgId') else ''
            prompt+=' Link the returned run ID at https://app.port.io/'+org_path+'organization/workflow-run?runId=<actual run ID>.'
        properties={'status':'active','description':purpose,'prompt':prompt,
            'tools':tools,'execution_mode':'Automatic','conversation_starters':[starter],
            'labels':{'demo':'j-claw','role':role,'workflow':'jclaw_decline',
                'source':'https://github.com/jbaruch/jclaw-devoxx'}}
        model=config['models'][role]
        if model:
            assert set(model)=={'provider','model'} and all(isinstance(v,str) and v for v in model.values())
            properties.update(model)
        agents.append({'identifier':AGENT_IDS[role],'title':title,'icon':'Code','properties':properties,
            'relations':{'mcp_servers':['jclaw-read'] if role=='identify' else []}})
    return agents

def build_workflow(config,target,profile):
    nodes=[]; edges=[]
    agents={agent['properties']['labels']['role']:agent for agent in build_agents(config)}
    def node(identifier,title,config): nodes.append({'identifier':identifier,'title':title,'config':config}); return identifier
    def edge(source,target,outlet=None):
        e={'sourceIdentifier':source,'targetIdentifier':target}
        if outlet:e['sourceOutletIdentifier']=outlet
        edges.append(e)
    def webhook(identifier,title,path,body):
        return node(identifier,title,{'type':'WEBHOOK','url':config['bridgeUrl'].rstrip('/')+path,
            'method':'POST','synchronized':True,'onFailure':'terminate',
            'headers':{'Authorization':'Bearer {{ .secrets["JCLAW_PORT_ACTION_TOKEN"] }}','Content-Type':'application/json'},'body':body})
    def ai(identifier,title,role,prompt,schema,tools=None):
        agent=agents[role];properties=agent['properties']
        assert (tools or ['^$'])==properties['tools'],'Workflow tools must match the declared agent'
        c={'type':'AI_AGENT','agentIdentifier':agent['identifier'],'userPrompt':prompt,'outputSchema':schema}
        if role=='identify':
            # Port agent MCP is interactive-only. Automated connector calls require
            # an AI node, generated from the same declared prompt/tools/model.
            c={'type':'AI','systemPrompt':properties['prompt'],'userPrompt':prompt,
                'tools':properties['tools'],'outputSchema':schema,
                'mcpServers':[{'identifier':server} for server in agent['relations']['mcp_servers']]}
            if 'model' in properties:c.update({key:properties[key] for key in ('provider','model')})
        return node(identifier,title,c)
    def condition(identifier,title,routes):
        node(identifier,title,{'type':'CONDITION','outlets':[
            {'identifier':outlet,'title':label,'expression':expr,
                'statusLabel':{'text':json.dumps(label),'variant':'success' if outlet=='approve' else 'alert'}}
            for outlet,label,expr in routes]})
    node('trigger','Get me out of training',{'type':'SELF_SERVE_TRIGGER','permissions':{'roles':['Admin','Member']},
        'userInputs':{'properties':{'request':dict(STR,title='Current request',default=REQUEST,
            description='The user\'s complete natural-language request verbatim, including the training/workshop, organizer and constraints. Do not replace it with an agent-generated plan.')},'required':['request']}})
    node('sent_history','Read actual sent records',{'type':'WEBHOOK','url':config['apiBase'].rstrip('/')+'/entities/search',
        'method':'POST','synchronized':True,'onFailure':'terminate',
        'body':{'combinator':'and','rules':[{'property':'$blueprint','operator':'=','value':'jclaw_sent_fact'},
            {'property':'organizerName','operator':'=','value':target['organizer']}]}})
    edge('trigger','sent_history')
    node('user_context','Read the user context',{'type':'WEBHOOK',
        'url':config['apiBase'].rstrip('/')+'/blueprints/jclaw_profile/entities/jclaw-baruch',
        'method':'GET','synchronized':True,'onFailure':'terminate'})
    edge('sent_history','user_context')
    profile=template(output('user_context')+'.entity.properties.context')
    history=template(output('sent_history')+'.entities | map(.properties | {eventId, organizerName, flavor, message, deliveredAt}) | (.)')
    ai('a0_identify','Identify the obligation','identify',
        'First execute jclaw-read_getCalendar with {}. Read the returned calendar, then execute '
        'jclaw-read_getOrganizerSensitivity with the selected event\'s exact organizer name. '
        'After discovery, CALL each tool; discovery is not evidence. Return only after both results. '
        'Copy the actual event ID and organizer. Set knownAttendees, recentlyUsedFlavors and '
        'previouslyProposedFlavors to empty arrays; calendar decline flags are not excuse history. '
        'The next node supplies actual sent facts. Current instruction: '+template('.outputs.trigger.request')+
        '\nUser context: '+profile, REQUEST_SCHEMA,
        ['jclaw-read_getCalendar','jclaw-read_getOrganizerSensitivity'])
    edge('user_context','a0_identify')
    webhook('a0_context','Canonical request + sent evidence','/context',{
        'identified':template(output('a0_identify','response')+' | fromjson'),
        'userInstruction':template('.outputs.trigger.request'),'sentFacts':history,'previouslyProposedFlavors':[]})
    edge('a0_identify','a0_context')

    def revision_state(previous):
        return '('+output(f'a{previous}_human_review')+'.refinement // '+output(f'a{previous}_review')+'.refinement)'
    def proof(human,review):
        return {'reviewToken':template(output(review)+'.reviewToken'),
            'candidateId':template(output(human,'responses[0].inputs.candidateId')),
            'selectedOutlet':template(output(human,'selectedOutlet.identifier')),
            'buttonIdentifier':template(output(human,'responses[0].buttonIdentifier')),
            'submittedBy':template(output(human,'responses[0].submitterInfo.submittedBy')+
                ' | if type == "string" then . else tojson end'),
            'workflowRunId':template('.workflowRun.identifier')}
    # Unroll one shared six-refinement budget. Either critic reaches the SAME next
    # Refine node; both see the same request and candidate at each global depth.
    for depth in range(MAX_REFINEMENTS + 1):
        pre=f'a{depth}'
        request=template(output('a0_context')) if depth==0 else template(revision_state(depth-1)+'.request')
        candidate=pre+'_draft' if depth==0 else pre+'_refine'
        verbatim='messageToOrganizer is sent VERBATIM and hallwayScript is spoken to the organizer. '
        verbatim+='No internal notes, user-facing asides or avoided-excuse lists in either field. '
        verbatim+='The human panel displays avoided lists separately. Set fakeCalendarEventId=null. '
        if depth==0:
            ai(candidate,'Draft a typed plan','draft',
                'Draft the best plan. Honor userInstruction and avoid both flavor lists. '+verbatim+
                '\nCurrent request: '+request+'\nUser context: '+profile,PLAN_SCHEMA)
            edge('a0_context',candidate)
        else:
            ai(candidate,f'Refine {depth} of {MAX_REFINEMENTS} — either critic','draft',
                'Revise this exact candidate using the critic feedback. Preserve the current event and organizer; '
                'honor the current userInstruction and both avoided-flavor lists. '+verbatim+
                '\nCurrent request: '+request+'\nPrevious plan: '+template(revision_state(depth-1)+'.plan')+
                '\nCritic feedback: '+template(revision_state(depth-1)+'.feedback')+'\nUser context: '+profile,PLAN_SCHEMA)
        judge=pre+'_judge';review=pre+'_review';route=pre+'_route';human=pre+'_human'
        ai(judge,'Judge the current candidate','judge',
            'Load skill jclaw-decline-review. Judge this exact candidate against the current request, latest human '
            'feedback and both avoided-flavor lists. Reject internal notes or user-facing asides in either script, '
            'even if labelled not in the email: messageToOrganizer is sent VERBATIM. Avoided lists are shown '
            'separately in the human panel. An honest draft may pass.\nCurrent request: '+request+
            '\nPlan: '+template(output(candidate,'response')+' | fromjson')+'\nUser context: '+profile,
            CRITIQUE_SCHEMA,['load_skill'])
        edge(candidate,judge)
        webhook(review,'Critic decision — shared refinement budget','/review',{
            'request':request,'plan':template(output(candidate,'response')+' | fromjson'),
            'critique':template('try ('+output(judge,'response')+' | fromjson) catch null'),
            'refinements':depth,'workflowRunId':template('.workflowRun.identifier')})
        edge(judge,review)
        condition(route,'Approve / refine / block',[(r,l,output(review)+'.route == '+json.dumps(r.upper())) for r,l in
            [('approve','Human critic next'),('refine','Shared refinement'),('block','Blocked — no delivery')]])
        edge(review,route);edge(route,human,'approve');edge(route,'blocked','block')
        edge(route,f'a{depth+1}_refine' if depth<MAX_REFINEMENTS else 'blocked','refine')
        approved=output(review)
        buttons=[{'identifier':'approve','label':'Send this exact message','variant':'PRIMARY'},
            {'identifier':'hold','label':'Hold — no action','variant':'SECONDARY'},
            {'identifier':'reject','label':'Reject — refine with feedback' if depth<MAX_REFINEMENTS else 'Reject — block (limit reached)','variant':'DANGER'}]
        node(human,'Human critic — exact candidate',{'type':'INPUT',
            'description':'Recipient: '+template(approved+'.prepared.send.organizerName')+'\nMessage:\n'+
                template(approved+'.prepared.send.message')+'\nHallway script:\n'+template(approved+'.prepared.plan.hallwayScript')+
                '\nAvoiding previously sent excuses: '+template(approved+'.prepared.request.recentlyUsedFlavors | join(", ")')+
                '\nAvoiding previously proposed alternatives: '+template(approved+'.prepared.request.previouslyProposedFlavors | join(", ")')+
                f'\nShared refinements used: {depth} of {MAX_REFINEMENTS}.\nCritic: '+template(approved+'.prepared.critique.feedback'),
            'responders':{'users':[config['approverEmail']]},'notifications':[],
            'userInputs':{'properties':{'feedback':dict(STR,title='Critic feedback — what should change?'),
                'candidateId':dict(STR,title='Exact candidate',readOnly=True,default=template(approved+'.prepared.send.candidateId'))},
                'buttons':buttons},
            'outlets':[{'identifier':b['identifier'],'title':b['label'],'evaluationMethod':'button','numOfResponders':1,
                'statusLabel':{'text':json.dumps(b['label']),'variant':'success' if b['identifier']=='approve' else 'alert'}} for b in buttons]})
        edge(human,'held','hold')
        human_review=pre+'_human_review';human_route=pre+'_human_route'
        webhook(human_review,'Human decision — same refinement budget','/review/human',
            proof(human,review) | {'feedback':template(output(human,'responses[0].inputs.feedback')+' // ""')})
        edge(human,human_review,'reject')
        condition(human_route,'Human reject / shared limit',[(r,l,output(human_review)+'.route == '+json.dumps(r.upper())) for r,l in
            [('refine','Shared refinement'),('block','Blocked — limit reached')]])
        edge(human_review,human_route);edge(human_route,'blocked','block')
        edge(human_route,f'a{depth+1}_refine' if depth<MAX_REFINEMENTS else 'blocked','refine')
        delivery=pre+'_deliver'
        webhook(delivery,'Send + validate matching receipt','/deliver',proof(human,review))
        edge(human,delivery,'approve')
        sent=output(delivery)+'.sentFact';receipt=output(delivery)+'.receipt'
        node(pre+'_remember','Write the confirmed sent fact',{'type':'UPSERT_ENTITY','blueprintIdentifier':'jclaw_sent_fact',
            'onFailure':'terminate','mapping':{'identifier':'jclaw-send-'+template(receipt+'.callId'),
                'title':'Confirmed decline — '+template(sent+'.eventId'),
                'properties':{f:template(sent+'.'+f) for f in ['eventId','organizerName','flavor','message','deliveredAt']} |
                    {'source':'Native Port workflow','candidateId':template(receipt+'.candidateId'),'callId':template(receipt+'.callId')},
                'relations':{'event':template(sent+'.eventId')}}})
        edge(delivery,pre+'_remember')
    for identifier,title in [('blocked','Blocked — shared refinement limit'),('held','Held — nothing sent')]:
        node(identifier,title,{'type':'CONDITION','outlets':[{
            'identifier':'stop','expression':'true','statusLabel':{'text':json.dumps(title+'. Nothing sent.'),'variant':'alert'}}]})
        edge(identifier,'stop','stop')
    node('stop','No external action',{'type':'WEBHOOK','url':config['bridgeUrl'].rstrip('/')+'/health',
        'method':'GET','synchronized':True,'onFailure':'terminate'})
    return {'identifier':'jclaw_decline','title':'j-claw — reviewed decline','icon':'Code','category':'Devoxx demo',
        'description':'Get the user out of a training session or workshop with a reviewed decline. Use when the user '
        'asks to get out of, skip, avoid or decline training, including Basic AI Proficiency Training run by Dana. '
        'Pass the complete user request verbatim. Identify once, then Draft → Judge → Human critic. Either rejection feeds the same Refine node '
            'and returns through both critics. Six refinements total per request; exhaustion blocks. '
            'Exact-candidate delivery and receipt-backed sent history.',
        'allowAnyoneToViewRuns':False,'nodes':nodes,'connections':edges}

def build_dashboard():
    return {'identifier':'jclaw-demo','title':'j-claw','icon':'Apps','visibility':'org',
        'description':'Ask j-claw to get you out of training, then review its proposal.',
        'widgets':[{'id':'jclaw-chat','type':'ai-agent','title':'Ask j-claw',
            'description':'Tell j-claw which training you want to skip and what it should consider.',
            'agentIdentifier':AGENT_IDS['entry'],'useMCP':True}],
        'layout':[{'height':400,'columns':[{'id':'jclaw-chat','size':12}]}]}

def build_home():
    def table(identifier,title,blueprint,entities,columns,icon):
        return {'id':identifier,'type':'table-entities-explorer','title':title,'icon':icon,
            'blueprint':blueprint,'displayMode':'widget',
            'dataset':{'combinator':'and','rules':[{'property':'$identifier','operator':'in','value':entities}]},
            'blueprintConfig':{blueprint:{'propertiesSettings':{'shown':columns,'order':columns},
                'sortSettings':{'sortBy':[{'property':'$title','order':'asc'}]}}}}
    return {'identifier':'$home','title':'Home','visibility':'org','widgets':[
        {'id':'jclaw-home-workflow','type':'workflow-card-widget','title':'j-claw workflow','icon':'Code',
            'description':'Identify → Draft → Judge → Human review. Either critic can request refinement.',
            'items':[{'workflowIdentifier':'jclaw_decline','triggerIdentifier':'trigger'}]},
        table('jclaw-home-agents','Workflow agents','_ai_agent',
            [AGENT_IDS[role] for role in ('identify','draft','judge')],['$title','model'],'Code'),
        table('jclaw-home-skills','j-claw skills','skill',
            ['jclaw-corporate-speak','jclaw-decline-review'],['$title','description'],'Learn'),
        dict(build_dashboard()['widgets'][0],id='jclaw-home-chat')],
        'layout':[
            {'height':400,'columns':[{'id':'jclaw-home-workflow','size':4},{'id':'jclaw-home-chat','size':8}]},
            {'height':400,'columns':[{'id':'jclaw-home-agents','size':6},{'id':'jclaw-home-skills','size':6}]}]}

def validate(workflow):
    nodes={n['identifier']:n for n in workflow['nodes']};assert len(nodes)==len(workflow['nodes'])
    outgoing={}
    for e in workflow['connections']:
        assert e['sourceIdentifier'] in nodes and e['targetIdentifier'] in nodes
        source=nodes[e['sourceIdentifier']]['config']
        if source['type'] in ('INPUT','CONDITION'):
            assert e.get('sourceOutletIdentifier') in [o['identifier'] for o in source['outlets']], 'Missing or invalid source outlet'
        key=(e['sourceIdentifier'],e.get('sourceOutletIdentifier'))
        assert key not in outgoing,'Port has no fan-out: '+str(key)
        outgoing[key]=e['targetIdentifier']
    for n in nodes.values():
        c=n['config']; typ=c['type']
        if typ in ('INPUT','CONDITION'):
            for o in c['outlets']:assert (n['identifier'],o['identifier']) in outgoing
        if typ=='AI':assert c['tools']==READ_TOOLS and c['mcpServers']==[{'identifier':'jclaw-read'}] and c['outputSchema']
        if typ=='AI_AGENT':
            role='judge' if n['identifier'].endswith('_judge') else 'draft'
            assert c['agentIdentifier']==AGENT_IDS[role] and c['outputSchema']
        if typ=='INPUT':assert c['notifications']==[]
    def ancestors(node,seen=None):
        seen=set() if seen is None else seen
        for e in workflow['connections']:
            if e['targetIdentifier']==node and e['sourceIdentifier'] not in seen:
                seen.add(e['sourceIdentifier']);ancestors(e['sourceIdentifier'],seen)
        return seen
    for a in range(MAX_REFINEMENTS + 1):
        assert f'a{a}_human' in ancestors(f'a{a}_deliver')
        assert f'a{a}_deliver' in ancestors(f'a{a}_remember')
        assert outgoing[(f'a{a}_human','approve')]==f'a{a}_deliver'
        assert outgoing[(f'a{a}_human','reject')]==f'a{a}_human_review'
        assert nodes[f'a{a}_review']['config']['body']['refinements']==a
        next_refine=f'a{a+1}_refine' if a<MAX_REFINEMENTS else 'blocked'
        assert outgoing[(f'a{a}_route','refine')]==next_refine
        assert outgoing[(f'a{a}_human_route','refine')]==next_refine
        assert outgoing[(f'a{a}_route','block')]=='blocked'
        assert outgoing[(f'a{a}_human_route','block')]=='blocked'
    assert [n for n in nodes if n.endswith('_identify')]==['a0_identify'],'A rejection must not restart Identify'
    visited=set();active=set()
    def visit(identifier):
        assert identifier not in active,'Unsupported cycle in the prepared DAG'
        if identifier in visited:return
        active.add(identifier)
        for e in workflow['connections']:
            if e['sourceIdentifier']==identifier:visit(e['targetIdentifier'])
        active.remove(identifier);visited.add(identifier)
    visit('trigger');assert visited==set(nodes),'Unreachable native workflow node'
    return {'nodes':len(nodes),'connections':len(workflow['connections']),'checks':'PASS'}

def prepare(config,destination):
    destination.mkdir(parents=True,exist_ok=True)
    events,target,profile,history=fixture()
    blueprints=[
        blueprint('jclaw_event','j-claw calendar event',{'start':dict(STR,format='date-time'),'organizer':STR,'declined':{'type':'boolean'}}),
        blueprint('jclaw_sent_fact','j-claw confirmed sent fact',{'eventId':STR,'organizerName':STR,'flavor':FLAVOR,'message':dict(STR,format='markdown'),
            'deliveredAt':dict(STR,format='date-time'),'source':STR,'candidateId':STR,'callId':STR},
            {'event':{'title':'Calendar event','target':'jclaw_event','required':False,'many':False}},
            ['eventId','organizerName','flavor','message','deliveredAt','source']),
        blueprint('jclaw_profile','j-claw user context',{'context':dict(STR,format='markdown')}),
        blueprint('skill','Skill',{'description':STR,'instructions':dict(STR,format='markdown'),
            'location':{'type':'string','enum':['global','project'],'default':'global'}})]
    entities=[{'blueprint':'jclaw_event','entities':[{'identifier':e['id'],'title':e['title'],
        'properties':{k:e[k] for k in ('start','organizer','declined')}} for e in events]},
        {'blueprint':'jclaw_sent_fact','entities':history},
        {'blueprint':'jclaw_profile','entities':[{'identifier':'jclaw-baruch','title':'Baruch — fictional demo context','properties':{'context':profile}}]}]
    skills=[]
    for f in [ROOT/'skills/corporate-speak/SKILL.md', PORT/'skills/jclaw-decline-review/SKILL.md']:
        _,front,body=f.read_text().split('---',2)
        name=re.search(r'^name: (.+)',front,re.M).group(1)
        description=re.search(r'^description: (.+)',front,re.M).group(1)
        skills.append({'identifier':'jclaw-'+name if not name.startswith('jclaw-') else name,'title':name,
            'properties':{'description':description,'instructions':body.strip(),'location':'global'}})
    entities.append({'blueprint':'skill','entities':skills})
    agents=build_agents(config)
    entities.append({'blueprint':'_ai_agent','entities':agents})
    connector={'blueprint':'_mcp_server','entities':[{'identifier':'jclaw-read','title':'j-claw read tools',
        'properties':{'url':config['bridgeUrl'].rstrip('/')+'/mcp','description':'Read-only calendar and organizer tools.',
            'headers':{'Authorization':'Bearer {{ .secrets["JCLAW_PORT_READ_TOKEN"] }}'},
            'allowed_tools':['jclaw-read_getCalendar','jclaw-read_getOrganizerSensitivity'],'exposed':True}}]}
    workflow=build_workflow(config,target,profile)
    manifest={'schema_version':1,'status':'LOCAL PREPARATION — not deployed or live-verified','fixture':{'eventId':target['id'],
        'organizerName':target['organizer'],'calendarEvents':len(events),'sentSeedRecords':len(history)},
        'models':config['models'],'modelNote':'null means Port organization default; discover and configure actual provider/model IDs before rehearsal. APIs differ from the JVM subscription CLIs.',
        'humanRetryNote':'Both critics share six refinements total. Each refinement returns through Judge and Human; exhaustion blocks.',
        'graphValidation':validate(workflow),'sources':{str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in
            [ROOT/'mocks/src/main/kotlin/jclaw/mocks/Store.kt', ROOT/'domain/src/main/kotlin/jclaw/domain/Scenario.kt', ROOT/'skills/corporate-speak/SKILL.md',
             PORT/'skills/jclaw-decline-review/SKILL.md', PORT/'prepare.py']}}
    for name,body in [('blueprints',blueprints),('entities',entities),('agents',agents),('connector',connector),('workflow',workflow),('dashboard',build_dashboard()),('home',build_home()),('manifest',manifest)]:
        (destination/(name+'.json')).write_text(json.dumps(body,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(manifest['graphValidation']))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--config',type=pathlib.Path,default=PORT/'config.example.json')
    p.add_argument('--out',type=pathlib.Path,default=PORT/'preview');a=p.parse_args()
    prepare(json.loads(a.config.read_text()),a.out)
