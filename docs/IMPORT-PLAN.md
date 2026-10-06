# Uvoz popisa sa slika

Izvor je korisnikov popis, a ne prehrambena preporuka aplikacije. Korisnik je potvrdio zajedničke ciljeve kategorija, donju granicu raspona i zasebno brojanje svakog sastojka jela. Potpuni početni katalog je u verziji 1.2; [JSON za import](../imports/lucijin-prehrambeni-popis.json) sadrži 12 kategorija i 84 namirnica.

## Prepisani ciljevi

| Kategorija | Cilj sa slike | Donja granica u 7 dana | Gornja granica u 7 dana |
| --- | --- | ---: | ---: |
| Povrće | 1–2+ porcije svaki dan | 7 | Nije određena: 2+ |
| Voće | 2 porcije svaki dan | 14 | 14 |
| Riba | 2–3 obroka tjedno | 2 | 3 |
| Jaja | 4–7 komada tjedno | 4 | 7 |
| Mahunarke | 2–4 obroka tjedno | 2 | 4 |
| Piletina / puretina | 2–4 obroka tjedno | 2 | 4 |
| Crveno meso | 1–2 obroka tjedno | 1 | 2 |
| Mliječni proizvodi | 1–2 porcije svaki dan | 7 | 14 |
| Orašasti plodovi | 4–7 dana tjedno | 4 različita dana | 7 različitih dana |
| Sjemenke | 2–4 puta tjedno | 2 | 4 |
| Žitarice | svaki dan | 7 različitih dana | 7 različitih dana |
| Krumpir / batat | 2–4 obroka tjedno | 2 | 4 |

Žitarice uključuju kruh, zobene pahuljice, muesli, rižu, tjesteninu, palentu, bulgur, kus-kus, ječam, proso i heljdu. Krumpir i batat ostaju odvojeni od žitarica. Jela ostaju zaseban navigacijski prečac, uz ovih 12 prehrambenih kategorija.

## Primijenjena pravila

- Tjedni ciljevi redom iz tablice: **7, 14, 2, 4, 2, 2, 1, 7, 4, 2, 7, 2**.
- Orašasti plodovi i žitarice broje različite dane. Ostale kategorije broje svaki Food unos. Jaje je konkretna namirnica; jedan klik bilježi jedan unos.
- Juha s brokulom, cvjetačom i mrkvom stvara tri unosa i povećava cilj Povrće za 3, prema korisnikovom odabiru. Grupni Undo i dalje briše samo taj klik jela.
- Cilj 14 za Voće znači dva unosa dnevno iz bilo kojeg izbora voća. Cilj 7 za Povrće i Mliječne proizvode znači jedan dnevno. Žitarice trebaju barem jedan unos svaki dan.
- Namirnice su primjeri za brzi izbor; ciljevi ne pripadaju svakoj namirnici zasebno. Sve se mogu uređivati i dopunjavati u Postavkama.
- Ugrađeni uvoz: **Postavke → Uvezi prehrambeni popis → PRIMIJENI**. Vanjska datoteka: **Postavke → Uvezi podatke → odaberi JSON popis → PRIMIJENI**.
- Import čuva povijest, jela, veze i postavke. Postojeće namirnice s istim nazivom zadržavaju ID i sliku; Piletina i Junetina premještaju se u nove odgovarajuće kategorije. Stara prazna kategorija Meso deaktivira se.

## Razlika u odnosu na prethodni model

Potpuni JSON backup zamjenjuje kategorije, namirnice, jela, veze, povijest i postavke. Nije namijenjen dopuni kataloga namirnica iz prehrambenog popisa. Prethodna verzija imala je ciljeve namirnica; verzija 1.1 ima ciljeve kategorija.

Zato se ova tablica ne može vjerno uvesti samo pretvorbom u postojeći backup. Cilj „Voće 14” ne smije se kopirati na svaku voćku: time bi se tražilo 14 jabuka **i** 14 banana, umjesto ukupno 14 unosa voća.

Za nove konkretne namirnice definirani su engleski `imageQuery` upiti. Fotografije automatski dohvaća postojeći pozadinski Pixabay servis. Slike same navode samo skupinu žitarica; ostale konkretne namirnice u JSON-u predstavljaju početni katalog koji korisnik može urediti, bez dodatnih ciljeva po namirnicama.

Promijenjeni su Room schema (v2 s migracijom), pravila za Danas/Tjedan, uređivač kategorija i backup (v2 s podrškom za uvoz v1). To je svjesna promjena izvorne specifikacije, prema novim korisnikovim odgovorima.
