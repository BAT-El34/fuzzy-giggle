import random, re

def split_rule(s):
    s=(s or '').strip()
    if ';' not in s:return s,''
    a,b=s.split(';',1);return a.strip(),b.strip()

def matches(msg,rule):
    a,b=split_rule(rule); low=msg.lower()
    return (not a or a.lower() in low) and (not b or msg.strip().lower().endswith(b.lower()))

def transform(msg,rule):
    a,b=split_rule(rule); out=msg
    if a: out=re.sub(re.escape(a),'',out,flags=re.I)
    if b and out.strip().lower().endswith(b.lower()): out=out.strip()[:-len(b)].strip()
    out=re.sub(r'[ \t]{2,}',' ',out).strip()
    return out

def simulate(chats, viewport, reject='Conserver', diagnostic=True):
    skipped=set(); processed=[]; scroll=0; max_scroll=18
    while True:
        visible=range(scroll, min(len(chats), scroll+viewport))
        found=None
        for i in visible:
            if chats[i]['draft'] and i not in skipped:
                found=i;break
        if found is None:
            if scroll+viewport>=len(chats) or scroll>=max_scroll:
                return processed, skipped
            scroll += max(1, viewport-1)
            continue
        m=chats[found]['text']
        if not matches(m,'Soko'):
            processed.append((found,'reject-diagnostic' if diagnostic else 'reject'))
            if diagnostic:
                return processed, skipped
            if reject=='Détruire': chats[found]['draft']=False
            else: skipped.add(found)
            continue
        t=transform(m,',Enregistré,')
        assert ',Enregistré,'.lower() not in t.lower()
        processed.append((found,'diagnostic' if diagnostic else 'sent'))
        if diagnostic:
            assert chats[found]['text'] == m
            return processed, skipped
        else:
            chats[found]['draft']=False

rng=random.Random(20260820)
for case in range(2000):
    n=rng.randint(1,80); viewport=rng.randint(3,12)
    chats=[]
    expected=0
    for i in range(n):
        draft=rng.random()<0.18
        good=rng.random()<0.7
        text=(f'Bonjour Soko {i} ,Enregistré, ok' if good else f'Bonjour {i} autre')
        chats.append({'draft':draft,'text':text})
        expected += int(draft)
    out, skipped=simulate(chats,viewport,'Conserver',True)
    assert len({i for i,_ in out})==len(out)
    assert len(out) <= 1
    assert all(action in {'diagnostic','reject-diagnostic'} for _,action in out)

for case in range(1000):
    n=rng.randint(1,50); viewport=rng.randint(3,10)
    chats=[{'draft':rng.random()<.22,'text':('Soko ,Enregistré, x' if rng.random()<.75 else 'x')} for _ in range(n)]
    out,_=simulate(chats,viewport,'Détruire',False)
    assert all(a in {'sent','reject'} for _,a in out)

print('PASS engine model: 3000 randomized conversation sequences')
