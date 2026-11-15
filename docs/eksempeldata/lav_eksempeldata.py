# Laver de opdigtede eksempeldata (startlister og logoer) til hjælpesidens skærmbilleder: python3 lav_eksempeldata.py <mappe>
import sys, random
from PIL import Image, ImageDraw, ImageFont
D = sys.argv[1]
FB = "/home/user/binumbergenerator/core/src/main/resources/fonts/DejaVuSans-Bold.ttf"
FR = "/home/user/binumbergenerator/core/src/main/resources/fonts/DejaVuSans.ttf"
random.seed(7)
given_f = ["Anna", "Maja", "Sofie", "Ida", "Emma", "Freja", "Laura", "Clara", "Mette", "Karen"]
given_m = ["Lars", "Mads", "Søren", "Jonas", "Emil", "Mikkel", "Anders", "Rasmus", "Henrik", "Ole"]
family = ["Skovgaard", "Bækgaard", "Lindholm", "Mosegaard", "Højbjerg", "Engholm", "Krogh", "Dalsgaard", "Bjerre", "Holm", "Kjær", "Lund", "Ravn", "Storm"]
clubs = ["OK Skovløberne", "Fjeldby OK", "Søbakken Orientering", "OK Granhøj"]
classes = [("D21", "3", 6200, "S1"), ("H21", "1", 8400, "S1"), ("D45", "4", 4800, "S2"), ("H45", "2", 6900, "S2"), ("D16", "5", 3900, "S1"), ("H16", "5", 4100, "S1")]
def person(cls):
    g = random.choice(given_f if cls.startswith("D") else given_m)
    return g, random.choice(family), random.choice(clubs)

def xml_single():
    out = ['<?xml version="1.0" encoding="UTF-8"?>', '<!-- Opdigtede eksempeldata -->',
           '<StartList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0" creator="Eksempel">',
           '<Event><Name>Skovløbet 2026</Name><StartTime><Date>2026-05-16</Date></StartTime></Event>']
    bib = 101; minute = 0
    for cls, course, length, start in classes:
        out.append(f'<ClassStart><Class><Name>{cls}</Name></Class><Course><Name>{course}</Name><Length>{length}</Length></Course><StartName>{start}</StartName>')
        for i in range(4):
            g, f, c = person(cls)
            # én tilmelding uden nummer (eftertilmeldt)
            b = "" if (cls == "H16" and i == 3) else f"<BibNumber>{bib}</BibNumber>"
            t = 10 * 60 + minute; minute += 2
            out.append(f'<PersonStart><Person><Name><Family>{f}</Family><Given>{g}</Given></Name></Person><Organisation><Name>{c}</Name></Organisation>'
                       f'<Start>{b}<StartTime>2026-05-16T{t//60:02d}:{t%60:02d}:00+02:00</StartTime><ControlCard>{8100000 + bib}</ControlCard></Start></PersonStart>')
            bib += 1
        out.append('</ClassStart>')
    out.append('</StartList>')
    open(f"{D}/skovloebet_startliste.xml", "w").write("\n".join(out) + "\n")

def xml_relay():
    out = ['<?xml version="1.0" encoding="UTF-8"?>', '<!-- Opdigtede eksempeldata -->',
           '<StartList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0" creator="Eksempel">',
           '<Event><Name>Skovstafetten 2026</Name><StartTime><Date>2026-09-05</Date></StartTime></Event>',
           '<ClassStart><Class><Name>Åben</Name></Class><StartName>S1</StartName>']
    for n, club in enumerate(clubs[:3], start=1):
        out.append(f'<TeamStart><Name>{club} {n}</Name><BibNumber>{20 + n}</BibNumber><Organisation><Name>{club}</Name></Organisation>')
        for leg in (1, 2, 3):
            g, f, _ = person(random.choice(["D", "H"]))
            st = f'<StartTime>2026-09-05T10:00:00+02:00</StartTime>' if leg == 1 else ''
            out.append(f'<TeamMemberStart><Person><Name><Family>{f}</Family><Given>{g}</Given></Name></Person>'
                       f'<Start><Leg>{leg}</Leg><BibNumber>{20 + n}-{leg}</BibNumber>{st}<Course><Name>{leg}A</Name><Length>{4200 + leg * 300}</Length></Course>'
                       f'<ControlCard>{8200000 + n * 10 + leg}</ControlCard></Start></TeamMemberStart>')
        out.append('</TeamStart>')
    out += ['</ClassStart>', '</StartList>']
    open(f"{D}/skovstafetten.xml", "w").write("\n".join(out) + "\n")

def xml_multiday():
    out = ['<?xml version="1.0" encoding="UTF-8"?>', '<!-- Opdigtede eksempeldata: tre etaper i én fil -->',
           '<StartList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0" creator="Eksempel">',
           '<Event><Name>Skovdagene 2026</Name><StartTime><Date>2026-07-03</Date></StartTime></Event>']
    bib = 201
    for cls, course, length, start in classes[:4]:
        out.append(f'<ClassStart><Class><Name>{cls}</Name></Class><StartName>{start}</StartName>')
        for i in range(3):
            g, f, c = person(cls)
            out.append(f'<PersonStart><Person><Name><Family>{f}</Family><Given>{g}</Given></Name></Person><Organisation><Name>{c}</Name></Organisation>')
            for r, day in enumerate(["03", "04", "05"]):
                m = 10 * 60 + r * 30 + (bib - 201) * 2
                out.append(f'<Start raceNumber="{r+1}"><BibNumber>{bib}</BibNumber><StartTime>2026-07-{day}T{m//60:02d}:{m%60:02d}:00+02:00</StartTime>'
                           f'<Course><Name>{course}</Name><Length>{length + r * 400}</Length></Course><ControlCard>{8300000 + bib}</ControlCard></Start>')
            out.append('</PersonStart>'); bib += 1
        out.append('</ClassStart>')
    out.append('</StartList>')
    open(f"{D}/skovdagene_alle_etaper.xml", "w").write("\n".join(out) + "\n")

def logo(name, w, h, bg, fg, lines, flag=False, shape="rect"):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    if shape == "rect": d.rounded_rectangle([0, 0, w - 1, h - 1], h // 6, fill=bg)
    else: d.ellipse([0, 0, h - 1, h - 1], fill=bg); 
    x0 = (h + 20) if shape != "rect" else 30
    if flag:
        s = int(h * 0.5); fx, fy = 30, (h - s) // 2
        d.rectangle([fx, fy, fx + s, fy + s], fill="white", outline=(60, 60, 60), width=4)
        d.polygon([(fx + s, fy), (fx + s, fy + s), (fx, fy + s)], fill=(238, 106, 27)); x0 = fx + s + 30
    y = h // 2 - sum(sz for _, sz in lines) // 2 - 6
    for text, sz in lines:
        f = ImageFont.truetype(FB, sz); d.text((x0, y), text, font=f, fill=fg); y += int(sz * 1.15)
    im.save(f"{D}/{name}.png")

xml_single(); xml_relay(); xml_multiday()
logo("logo_skovloebet", 640, 260, (238, 106, 27), "white", [("SKOVLØBET", 70), ("2026", 54)], flag=True)
logo("logo_skovloeberne", 520, 260, (47, 107, 47), "white", [("OK", 64), ("Skovløberne", 58)])
logo("sponsor_bageri", 600, 180, (255, 248, 230), (120, 70, 20), [("Bageriet", 64), ("ved skoven", 40)])
logo("sponsor_cykel", 600, 180, (30, 58, 138), "white", [("Cykel & Sport", 60)])
