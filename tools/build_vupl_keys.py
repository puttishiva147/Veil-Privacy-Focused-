#!/usr/bin/env python3
"""Rebuild the canonical VUPL v1 ordered dictionary from the supplied source generator."""
import hashlib, json, os, unicodedata as ud
EN="""I you he she it we they me him her us them my your his its our their a an the and or but if then so because of in on at to from by with for about as is are was were be been am do does did have has had will would can could should may might must not no yes this that these those here there what who whom when where why how which all any some each every more most much many few one two three four five six seven eight nine ten hello hi hey bye goodbye good bad great nice fine okay ok please sorry thanks thank welcome love like want need know think see look come go get give make take say tell ask call help work play eat drink sleep wake live friend family mother father brother sister son daughter man woman boy girl child people name day night morning evening afternoon today tomorrow yesterday now later soon always never again very too also just only still already home house school office city country world time year month week hour minute money food water phone message chat video call app new old big small long short high low hot cold happy sad angry tired busy free ready sure right wrong true false first last next before after up down out over under between again online offline private secure code language"""
HI="नमस्ते नमस्कार धन्यवाद शुक्रिया कृपया माफ़ कीजिए हाँ नहीं आप तुम मैं हम वह यह क्या कौन कब कहाँ क्यों कैसे कैसा कैसी कैसे हैं है हूँ हो था थी थे और या लेकिन में से को का की के पर साथ अच्छा बुरा सुबह शाम रात आज कल दोस्त परिवार प्यार घर काम पानी खाना समय नाम भाई बहन माँ पिता बेटा बेटी लोग शुभ"
MR="तू तुम्ही मी आम्ही कसा कशी कसे आहेस आहे आहात काय कोण कधी कुठे का नमस्कार धन्यवाद होय नाही चांगला सकाळ संध्याकाळ रात्र आज उद्या मित्र कुटुंब प्रेम घर काम पाणी जेवण नाव"
TE="నువ్వు మీరు నేను మేము ఎలా ఉన్నావు ఉన్నారు ఏమిటి ఎవరు ఎప్పుడు ఎక్కడ ఎందుకు నమస్కారం ధన్యవాదాలు అవును కాదు మంచి ఉదయం సాయంత్రం రాత్రి ఈరోజు రేపు స్నేహితుడు కుటుంబం ప్రేమ ఇల్లు పని నీరు భోజనం పేరు"
ES="hola adiós gracias por favor perdón sí no yo tú él ella nosotros usted cómo estás está qué quién cuándo dónde por qué bien mal buenos días buenas noches tarde mañana hoy ayer amigo familia amor casa trabajo agua comida nombre tiempo"
AR="مرحبا كيف حالك شكرا من فضلك نعم لا أنا أنت هو هي ماذا من متى أين لماذا صباح مساء الخير صديق عائلة حب بيت عمل ماء طعام اسم"
JZ="こんにちは ありがとう さようなら はい いいえ おはよう こんばんは 你好 谢谢 再见 是 不是 早上好 晚上好 朋友 家人 爱 안녕하세요 감사합니다"
PHRASES=["How are you?","I love you.","Good morning.","Good morning","Good night.","Good night","See you later.","See you later","See you","Thank you.","Thank you","Thank you very much","Take care","Nice to meet you","What is your name?","I am fine","I am sorry","Please help me","Happy birthday","Good luck","Let me know","On my way","Call me","Talk to you later","आप कैसे हैं?","आप कैसे हैं","धन्यवाद","शुभ प्रभात","शुभ रात्रि","फिर मिलेंगे","मैं आपसे प्यार करता हूँ","तू कसा आहेस?","तू कसा आहेस","¿Cómo estás?","Buenos días","Buenas noches","Hasta luego","Te quiero","నువ్వు ఎలా ఉన్నావు?","నువ్వు ఎలా ఉన్నావు","كيف حالك؟","صباح الخير","شكرا جزيلا"]
FLAGS="AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS XK YE YT ZA ZM ZW".split()
EMO_R=[(0x2190,0x21FF),(0x2300,0x23FF),(0x2600,0x27BF),(0x2B00,0x2BFF),(0x1F000,0x1FAFF)]
def assigned(c): return ud.category(chr(c)) not in ("Cn","Co","Cs","Cc","Cf","Zl","Zp")
def build():
    keys=[]; seen=set()
    def add(ns,val):
        k=f"{ns}:{val}"
        if k not in seen: seen.add(k); keys.append(k)
    for m in ("NUM","END","CP"): add("x",m)
    for ch in "0123456789.-": add("d",ch)
    for ch in " \n\t\r": add("ws",ch)
    for c in range(32,0x110000):
        if 0xD800<=c<=0xDFFF or 0x2EBF0<=c<=0x2EE5D or c in (0x2FFC,0x2FFD,0x2FFE,0x2FFF,0x31EF) or not assigned(c): continue
        ch=chr(c); cat=ud.category(ch)
        if cat[0] in "LMN": add("c",ch)
        elif cat[0] in "PS": add("s",ch)
        elif cat=="Zs" and ch!=" ": add("ws",ch)
    for a,b in EMO_R:
        for c in range(a,b+1):
            if assigned(c): add("e",chr(c)); add("e",chr(c)+"\ufe0f")
    for c in (0x200D,0xFE0F,0xFE0E,0x20E3,0xE0020,0xE007F): add("e",chr(c))
    for f in FLAGS: add("e",chr(0x1F1E6+ord(f[0])-65)+chr(0x1F1E6+ord(f[1])-65))
    for ch in "#*0123456789": add("e",ch+"\ufe0f\u20e3")
    for w in sorted(set(EN.split())):
        for v in dict.fromkeys([w,w.capitalize(),w.upper()]): add("w",v)
    for lst,lang in ((HI,"hi"),(MR,"mr"),(TE,"te"),(ES,"es"),(AR,"ar"),(JZ,"cjk")):
        for w in lst.split():
            add("w",w)
            if lang=="es": add("w",w.capitalize())
    for p in PHRASES: add("p",p)
    return keys
if __name__=="__main__":
    keys=build()
    if len(keys)!=157116:
        raise SystemExit(f"Unicode database mismatch: expected 157116 keys, generated {len(keys)} using Unicode {ud.unidata_version}")
    os.makedirs("app/src/main/assets", exist_ok=True)
    with open("app/src/main/assets/veil_keys_v1.json","w",encoding="utf-8") as f:
        json.dump({"protocol":"VEIL/1","version":1,"count":len(keys),"keys":keys},f,ensure_ascii=False,separators=(",",":"))
    digest=hashlib.sha256(open("app/src/main/assets/veil_keys_v1.json","rb").read()).hexdigest()
    if digest!="ea416c4efe5022bdad61833aa1fbf3f54a7475b9645f9d5110800c7f88a49510":
        raise SystemExit(f"VUPL v1 dictionary checksum mismatch: {digest}")
    print(f"Generated canonical VUPL dictionary: {len(keys)} ordered entries (Unicode {ud.unidata_version})")
