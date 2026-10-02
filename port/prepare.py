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
FLAVORS = ['CALENDAR_CONFLICT', 'FAMILY_OBLIGATION', 'CUSTOMER_ESCALATION', 'DEADLINE', 'ALREADY_PROFICIENT', 'EXISTENTIAL_CRISIS']
TIERS = ['AIRTIGHT', 'CREDIBLE', 'THIN', 'HR_WILL_NOTICE']
REQUEST = "Get me out of the Basic AI Proficiency Training on Tuesday, run by Dana from People Ops. Don't reuse an excuse I've already used on her - tell me which ones you're avoiding."

def obj(properties, required=None):
    return {'type':'object', 'properties':properties, 'required':required or list(properties), 'additionalProperties':False}

STR = {'type':'string'}
FLAVOR = {'type':'string','enum':FLAVORS}
ARRAY_FLAVOR = {'type':'array','items':FLAVOR}
REQUEST_SCHEMA = obj({'eventId':STR,'recentlyUsedFlavors':ARRAY_FLAVOR,'knownAttendees':{'type':'array','items':STR},
    'organizerName':STR,'userInstruction':STR,'previouslyProposedFlavors':ARRAY_FLAVOR})
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

def build_workflow(config,target,profile):
    nodes=[]; edges=[]
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
        c={'type':'AI','systemPrompt':'You are j-claw. Use only supplied facts and return the requested typed result. Never take an external action.',
            'userPrompt':prompt,'tools':tools or ['^$'],'outputSchema':schema}
        model=config['models'][role]
        if model:
            assert set(model)=={'provider','model'} and all(isinstance(v,str) and v for v in model.values())
            c.update(model)
        if role=='identify':c['mcpServers']=[{'identifier':'jclaw-read'}]
        return node(identifier,title,c)
    def condition(identifier,title,routes):
        node(identifier,title,{'type':'CONDITION','outlets':[
            {'identifier':outlet,'title':label,'expression':expr,
                'statusLabel':{'text':json.dumps(label),'variant':'success' if outlet=='approve' else 'alert'}}
            for outlet,label,expr in routes]})
    node('trigger','Get me out of training',{'type':'SELF_SERVE_TRIGGER','permissions':{'roles':['Admin','Member']},
        'userInputs':{'properties':{'request':dict(STR,title='Current request',default=REQUEST)},'required':['request']}})
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
    last='user_context'
    # One finite human replacement, with two automated refinements per candidate.
    # This is a readable DAG, not an unverified native cyclic-loop claim.
    for attempt in range(2):
        pre=f'a{attempt}'
        instruction=template('.outputs.trigger.request') if not attempt else template(
            '.outputs.trigger.request + "\\nHuman feedback: " + (.outputs.a0_human.responses[0].inputs.feedback // "Try another approach")')
        proposed=[] if not attempt else template(output('a0_ready')+'.prepared.plan.flavor | [.]')
        identify=pre+'_identify'
        ai(identify,'Identify the obligation' if not attempt else 'Identify again with human feedback','identify',
            'Call jclaw-read_getCalendar and jclaw-read_getOrganizerSensitivity. Identify the selected obligation; '
            'copy its event ID and exact organizer. Calendar decline flags are not excuse history. '
            'Return a DeclineRequest. Current instruction: '+instruction+'\nUser context: '+profile,
            REQUEST_SCHEMA,['jclaw-read_getCalendar','jclaw-read_getOrganizerSensitivity'])
        edge(last,identify,'try_another' if attempt else None)
        context=pre+'_context'
        webhook(context,'Canonical request + sent evidence','/context',{'identified':template(output(identify,'response')+' | fromjson'),
            'userInstruction':instruction,'sentFacts':history,'previouslyProposedFlavors':proposed})
        edge(identify,context)
        request=template(output(context))
        candidate=pre+'_draft'
        ai(candidate,'Draft a typed plan','draft','Draft the best plan. Honor userInstruction and avoid recentlyUsedFlavors '
            'and previouslyProposedFlavors. Write messageToOrganizer and hallwayScript. Set fakeCalendarEventId=null; '
            'drafting creates no calendar event.\nCurrent request: '+request+'\nUser context: '+profile,PLAN_SCHEMA)
        edge(context,candidate)
        validators=[]
        for refinement in range(3):
            judge=f'{pre}_judge_{refinement}'
            ai(judge,'Judge the current candidate'+(f' (refinement {refinement})' if refinement else ''),'judge',
                'Load skill jclaw-decline-review. Judge this exact plan against the current request, including latest '
                'human constraints and both avoided-flavor lists. An honest first draft may pass. Approve only when '
                'this exact candidate is ready for the human to consider sending.\nCurrent request: '+request+
                '\nPlan: '+template(output(candidate,'response')+' | fromjson')+'\nUser context: '+profile,
                CRITIQUE_SCHEMA,['load_skill'])
            edge(candidate,judge)
            validate=f'{pre}_review_{refinement}'
            webhook(validate,'Validate verdict + retry bound','/review',{'request':request,
                'plan':template(output(candidate,'response')+' | fromjson'),
                'critique':template('try ('+output(judge,'response')+' | fromjson) catch null'),
                'refinements':refinement,'workflowRunId':template('.workflowRun.identifier')})
            edge(judge,validate);validators.append(validate)
            route=f'{pre}_route_{refinement}'
            prefix=output(validate)+'.route'
            condition(route,'Approved / refine / blocked',[(r,l,prefix+' == '+json.dumps(r.upper())) for r,l in
                [('approve','Reviewed proposal'),('refine','Refine candidate'),('block','Blocked — no delivery')]])
            edge(validate,route);edge(route,pre+'_ready','approve');edge(route,pre+'_blocked','block')
            if refinement<2:
                new=f'{pre}_refine_{refinement+1}'
                ai(new,f'Refine {refinement+1} of 2','draft','Revise the plan using the judge feedback. Honor the same current '
                    'request, userInstruction and avoided flavors. Return the complete typed plan; no external actions. '
                    'Set fakeCalendarEventId=null.\nRequest: '+request+'\nPrevious plan: '+
                    template(output(candidate,'response')+' | fromjson')+'\nFeedback: '+template(output(validate)+'.feedback')+
                    '\nUser context: '+profile,PLAN_SCHEMA)
                edge(route,new,'refine');candidate=new
            else:edge(route,pre+'_blocked','refine')
        # Find the review whose real route is APPROVE, never a prior rejected candidate.
        selected='['+', '.join(output(v) for v in validators)+'] | map(select(.route == "APPROVE")) | if length == 1 then .[0] else error("No unique approved candidate") end'
        webhook(pre+'_ready','Bind the reviewed candidate','/review',{
            'request':request,'plan':template('('+selected+') | .prepared.plan'),
            'critique':template('('+selected+') | .prepared.critique'),
            'refinements':template('('+selected+') | .prepared.refinements'),
            'workflowRunId':template('.workflowRun.identifier')})
        approved=output(pre+'_ready')
        human=pre+'_human'
        buttons=[{'identifier':'approve','label':'Send this exact message','variant':'PRIMARY'},
            {'identifier':'hold','label':'Hold — no action','variant':'SECONDARY'},
            {'identifier':'try_another','label':'Try another approach' if not attempt else 'Hold and start a new request','variant':'DANGER'}]
        node(human,'Human review — exact candidate',{'type':'INPUT',
            'description':'Recipient: '+template(approved+'.prepared.send.organizerName')+'\nMessage:\n'+
                template(approved+'.prepared.send.message')+'\nHallway script:\n'+template(approved+'.prepared.plan.hallwayScript')+
                '\nCritic: '+template(approved+'.prepared.critique.feedback'),
            'responders':{'users':[config['approverEmail']]},'notifications':[],
            'userInputs':{'properties':{'feedback':dict(STR,title='What should change?'),
                'candidateId':dict(STR,title='Approved candidate',readOnly=True,default=template(approved+'.prepared.send.candidateId'))},
                'buttons':buttons},
            'outlets':[{'identifier':b['identifier'],'title':b['label'],'evaluationMethod':'button','numOfResponders':1,
                'statusLabel':{'text':json.dumps(b['label']),'variant':'success' if b['identifier']=='approve' else 'alert'}} for b in buttons]})
        edge(pre+'_ready',human)
        delivery=pre+'_deliver'
        webhook(delivery,'Send + validate matching mock receipt','/deliver',{
            'reviewToken':template(approved+'.reviewToken'),
            'candidateId':template('.outputs.'+human+'.responses[0].inputs.candidateId'),
            'selectedOutlet':template('.outputs.'+human+'.selectedOutlet.identifier'),
            'buttonIdentifier':template('.outputs.'+human+'.responses[0].buttonIdentifier'),
            'submittedBy':template('.outputs.'+human+'.responses[0].submitterInfo.submittedBy | if type == "string" then . else tojson end'),
            'workflowRunId':template('.workflowRun.identifier')})
        edge(human,delivery,'approve')
        sent=output(delivery)+'.sentFact'; receipt=output(delivery)+'.receipt'
        node(pre+'_remember','Write the confirmed sent fact',{'type':'UPSERT_ENTITY','blueprintIdentifier':'jclaw_sent_fact',
            'onFailure':'terminate','mapping':{'identifier':'jclaw-send-'+template(receipt+'.callId'),
                'title':'Confirmed decline — '+template(sent+'.eventId'),
                'properties':{f:template(sent+'.'+f) for f in ['eventId','organizerName','flavor','message','deliveredAt']} |
                    {'source':'Native Port workflow','candidateId':template(receipt+'.candidateId'),'callId':template(receipt+'.callId')},
                'relations':{'event':template(sent+'.eventId')}}})
        edge(delivery,pre+'_remember')
        node(pre+'_blocked','Blocked — no human override',{'type':'CONDITION','outlets':[{
            'identifier':'blocked','expression':'true','statusLabel':{'text':'"Blocked. Nothing sent."','variant':'alert'}}]})
        node(pre+'_held','Held — nothing sent',{'type':'CONDITION','outlets':[{
            'identifier':'held','expression':'true','statusLabel':{'text':'"Held. Nothing sent."','variant':'alert'}}]})
        # Terminal outlets use a native read-only webhook, so all declared outlets are connected.
        node(pre+'_stop','No external action',{'type':'WEBHOOK','url':config['bridgeUrl'].rstrip('/')+'/health',
            'method':'GET','synchronized':True,'onFailure':'terminate'})
        edge(pre+'_blocked',pre+'_stop','blocked');edge(pre+'_held',pre+'_stop','held');edge(human,pre+'_held','hold')
        if attempt:edge(human,pre+'_held','try_another')
        last=human
    return {'identifier':'jclaw_decline','title':'j-claw — reviewed decline','icon':'Code','category':'Devoxx demo',
        'description':'Same fictional task: actual read evidence, typed stages, two refinements, native human approval, '
            'exact-candidate mock delivery and receipt-backed sent history. One human replacement is expanded in this DAG; '
            'another replacement stops for a fresh request. Port invokes APIs, not the subscription CLIs.',
        'allowAnyoneToViewRuns':False,'nodes':nodes,'connections':edges}

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
        if typ=='AI':assert c['tools'] and c['outputSchema']
        if typ=='INPUT':assert c['notifications']==[]
    def ancestors(node,seen=None):
        seen=set() if seen is None else seen
        for e in workflow['connections']:
            if e['targetIdentifier']==node and e['sourceIdentifier'] not in seen:
                seen.add(e['sourceIdentifier']);ancestors(e['sourceIdentifier'],seen)
        return seen
    for a in range(2):
        assert f'a{a}_human' in ancestors(f'a{a}_deliver')
        assert f'a{a}_deliver' in ancestors(f'a{a}_remember')
        assert outgoing[(f'a{a}_human','approve')]==f'a{a}_deliver'
        assert outgoing[(f'a{a}_route_2','block')]==f'a{a}_blocked'
    assert outgoing[('a0_human','try_another')]=='a1_identify'
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
    connector={'blueprint':'_mcp_server','entities':[{'identifier':'jclaw-read','title':'j-claw read-only mocks',
        'properties':{'url':config['bridgeUrl'].rstrip('/')+'/mcp','description':'Read-only calendar and organizer from the same JVM mock servers.',
            'headers':{'Authorization':'Bearer {{ .secrets["JCLAW_PORT_READ_TOKEN"] }}'},
            'allowed_tools':['jclaw-read_getCalendar','jclaw-read_getOrganizerSensitivity'],'exposed':True}}]}
    workflow=build_workflow(config,target,profile)
    manifest={'schema_version':1,'status':'LOCAL PREPARATION — not deployed or live-verified','fixture':{'eventId':target['id'],
        'organizerName':target['organizer'],'calendarEvents':len(events),'sentSeedRecords':len(history)},
        'models':config['models'],'modelNote':'null means Port organization default; discover and configure actual provider/model IDs before rehearsal. APIs differ from the JVM subscription CLIs.',
        'humanRetryNote':'One replacement is explicitly expanded; a further rejection stops for a new request.',
        'graphValidation':validate(workflow),'sources':{str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in
            [ROOT/'mocks/src/main/kotlin/jclaw/mocks/Store.kt', ROOT/'domain/src/main/kotlin/jclaw/domain/Scenario.kt', ROOT/'skills/corporate-speak/SKILL.md']}}
    for name,body in [('blueprints',blueprints),('entities',entities),('connector',connector),('workflow',workflow),('manifest',manifest)]:
        (destination/(name+'.json')).write_text(json.dumps(body,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(manifest['graphValidation']))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--config',type=pathlib.Path,default=PORT/'config.example.json')
    p.add_argument('--out',type=pathlib.Path,default=PORT/'preview');a=p.parse_args()
    prepare(json.loads(a.config.read_text()),a.out)
