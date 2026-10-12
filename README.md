# Zdrava Lucija

Native Android aplikacija na hrvatskom za osobni, vizualni checklist prehrane. Svi podaci su lokalni. Android 8.0 ili noviji.

Pregled na emulatoru sa stvarnim Pixabay fotografijama: [Danas · svijetli izgled](docs/screenshots/today-light.png), [Danas · tamni izgled](docs/screenshots/today-dark.png), [Povrće](docs/screenshots/vegetables.png), [Tjedan](docs/screenshots/week-dark.png), [Danas bez interneta](docs/screenshots/today-offline.png).

## Korištenje

- **DANAS:** preostali dnevni unosi po kategorijama i svi današnji unosi konkretnih namirnica. Dodir kategorije otvara namirnice; dodir namirnice odmah bilježi unos.
- **+ → kategorija → namirnica:** jedan dodir = jedan unos. Možeš dodati više namirnica, zatim dodirnuti **GOTOVO**.
- **Jela:** jedan dodir bilježi sve sastavne namirnice u jednoj transakciji. **UNDO** briše samo zapise tog dodira.
- **TJEDAN:** zajednički ciljevi kategorija, preostali unosi ili dani i ostvareni ciljevi.
- **POVIJEST → Dani:** pregled unosa i ostvarenih dnevnih ciljeva; otvori dan pa **Dodaj hranu za ovaj dan** za naknadni unos namirnice ili jela. Prikazuju se i posljednjih sedam dana bez unosa; **Odaberi datum** otvara bilo koji prethodni dan. Budući datumi nisu dopušteni.
- **POVIJEST → Tjedni:** ostvarenost tjednih ciljeva, preostali unosi/dani i pregled pojedinačnih dana ciklusa. Iz dnevnog pregleda možeš otvoriti pripadajući tjedan. Pojedinačno brisanje i grupni Undo jela vrijede i za prethodne dane.
- **POSTAVKE:** namirnice, ciljevi kategorija, jela i njihove namirnice, prehrambeni popis, prvi dan tjedna, izgled i JSON backup.
- **Druga fotografija:** Postavke → Namirnice / Kategorije / Jela → odaberi stavku → **Dohvati drugu sliku**. Gumb bira sljedeću Pixabay fotografiju za engleski naziv i odmah je sprema za offline prikaz. Ako dohvat ne uspije ili nema alternative, trenutna fotografija ostaje spremljena. Novi naziv najprije spremi.
- **Vlastita fotografija:** u istom uređivanju dodirni **Odaberi svoju sliku**. Otvara se Androidov odabir fotografije; odabrana slika odmah se kopira u aplikaciju i koristi bez interneta. Odustajanje ili nevaljana datoteka zadržavaju prethodnu sliku. Za novu stavku najprije spremi naziv. Vlastitu sliku možeš kasnije zamijeniti drugom svojom ili Pixabay fotografijom.

[Prikaz uređivanja fotografije](docs/screenshots/image-editor.png).

Povijesni pregled računa ciljeve prema trenutačnim kategorijama, njihovim ciljevima i odabranom početku tjedna. Ranije verzije ciljeva nisu zasebno spremljene.

JSON sigurnosna kopija uključuje vlastite fotografije. One ostaju povezane sa stavkom i nakon promjene naziva. Aplikacija ne traži pristup cijeloj galeriji.

Default ciklus je ponedjeljak–nedjelja. Cilj kategorije `0` dopušta evidentiranje bez obveznog cilja. Svaka namirnica povećava broj svoje kategorije za 1, uključujući svaki sastojak jela. Za orašaste plodove i žitarice broje se različiti datumi. Dnevni prikaz izvodi se iz tjednog cilja: 7 znači 1 dnevno, 14 znači 2 dnevno; ciljevi koji nisu višekratnik 7 prikazuju se samo tjedno. Nema zasebnog daily target modela.

## Uvoz Lucijinog popisa

**Postavke → Uvezi prehrambeni popis → PRIMIJENI** ugrađuje popis sa slika: 12 kategorija i 84 konkretnih namirnica. Primjenjuje donju granicu raspona. Namirnice su ponuđeni izbori za unos, a ne pojedinačne obveze.

Alternativno spremi [JSON popis](imports/lucijin-prehrambeni-popis.json) na uređaj i odaberi **Postavke → Uvezi podatke**. Aplikacija prepoznaje format `dietPlan` i prikazuje isti pregled prije primjene. Ovaj import dopunjuje katalog i ažurira ciljeve; čuva povijest, postojeća jela, fotografije i postavke. Ponovljeni uvoz ne duplicira stavke s istim nazivom. Potpuni JSON backup i dalje zamjenjuje sve podatke nakon zasebne potvrde.

Detalji ciljeva i brojanja: [plan uvoza](docs/IMPORT-PLAN.md).

## Pokretanje projekta

1. Otvori ovu mapu u Android Studiju.
2. Koristi JDK 17 ili kompatibilni ugrađeni JDK Android Studija; instaliraj Android SDK 34.
3. Android Studio automatski stvara `local.properties` s putanjom SDK-a.
4. Sync Gradle, odaberi emulator ili uređaj i pokreni `app`.

Projekt uključuje Gradle wrapper. U PowerShellu, kada je Java dostupna:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

APK se nalazi u `app/build/outputs/apk/debug/app-debug.apk`.

## Pixabay ključ i slike

1. Nabavi vlastiti API ključ na [Pixabay API](https://pixabay.com/api/docs/).
2. Kopiraj `secrets.properties.example` u `secrets.properties` u korijenu projekta.
3. Upiši `PIXABAY_API_KEY=tvoj_kljuc` i ponovno izgradi aplikaciju.

Alternativno postavi environment varijablu `PIXABAY_API_KEY`. Ključ nije u Kotlin sourceu niti u verzioniranoj konfiguraciji. Development ključ ugrađuje se u APK putem BuildConfiga; APK ga ne može kriptografski sakriti. Nemoj dijeliti APK s privatnim ključem ako to ne želiš.

Bez ključa aplikacija potpuno radi s vizualnim fallbackovima. Novi zapisi se odmah spremaju; WorkManager dohvaća fotografije kada internet postane dostupan. Upiti koriste engleski `imageQuery`. Ako nije postavljen, naziv namirnice, jela ili kategorije automatski se prevodi s hrvatskog na engleski i upit se sprema prije Pixabay pretrage. Prijevod radi lokalno preko [ML Kit Translation](https://developers.google.com/ml-kit/language/translation/android); pri prvom prijevodu u pozadini preuzima jezični model, a kasnije ga koristi offline. Neuspješan prijevod ne šalje hrvatski upit na Pixabay. Spremanje hrane ostaje odmah dostupno. Početni dataset već ima engleske upite. Kad je fotografija pronađena, njen URL ostaje u bazi; slika se preuzima u privatne datoteke uređaja i prikazuje kroz Coil. Slike ne ovise o mreži nakon uspješnog preuzimanja, čak ni ako se Coil cache isprazni.

Pixabay [traži cacheiranje API odgovora 24 sata i zabranjuje trajno hotlinkanje](https://pixabay.com/api/docs/). Implementacija zato čuva rezultate upita 24 sata i lokalno preuzima odabrane slike. Ne pretražuje pri renderiranju kartica. Neuspjeli pokušaji imaju backoff i dnevni ponovni pokušaj; postojeće fotografije ne mijenjaju se na svakom pokretanju. Ako nakon uvoza na novom uređaju stari URL više nije dostupan, briše se taj nedostupan URL i pozadinski dohvat pokušava ponovno.

## Backup

U Postavkama odaberi **Izvezi podatke** i spremi JSON putem Android document pickera. Kopija uključuje kategorije, namirnice, ciljeve, jela, veze, cijelu povijest sa source/group podacima te prvi dan tjedna i izgled. Fotografije se ne ugrađuju u JSON; URL-ovi ostaju zapisani, a na novom uređaju slike se ponovno preuzimaju.

**Uvezi podatke** prvo provjerava format, cijele brojeve, datume, reference i grupe. Prikazuje broj zapisa i traži potvrdu zamjene trenutnih podataka. Room zamjena podataka izvršava se u jednoj transakciji. Preference se pohranjuju u DataStore; u slučaju prijavljene greške prethodne preference se vraćaju. Room i DataStore nisu jedan zajednički transactional store, pa prisilni prekid procesa u trenutku uvoza može zahtijevati ponovni uvoz postavki.

## Arhitektura i početni podaci

Kotlin · Compose · Material 3 · Navigation Compose · Room · ViewModel/StateFlow · Coroutines · DataStore · Coil · OkHttp · WorkManager.

Početni podaci su u `app/src/main/assets/seed/`: `categories.json`, `foods.json`, `meals.json`. Nova instalacija odmah dobiva 12 prehrambenih kategorija, jednu navigaciju Jela i svih 84 traženih namirnica s engleskim upitima za fotografije. Pri nadogradnji na 1.2 postojeći katalog automatski se dopunjuje jednom, uz očuvanje povijesti, jela, fotografija, ciljeva i aktivnih/neaktivnih izbora. Stari nazivi poput Orah/Orasi, Badem/Bademi i Zob/Zobene pahuljice povezuju se aliasima, bez dupliciranja ID-jeva. Namjerno vraćen prazan backup ostaje prazan. Uređivači koriste aktiviranje/deaktiviranje kako bi povijest i veze jela ostale valjane.

Room sheme v1 i v2 verzionirane su u `app/schemas/`. Migracija 1 → 2 čuva povijest i veze te privremeno zbraja stare ciljeve namirnica u kategorije; prehrambeni import zatim postavlja odabrane ciljeve. Stari Food `weeklyTarget` stupac ostaje radi kompatibilnosti, ali ne sudjeluje u brojanju niti ima uređivač. Backup format v2 čuva ciljeve kategorija; uvoz v1 kopije automatski ih pretvara. Nema destruktivnog migration fallbacka.

Detalji: [arhitektura](docs/ARCHITECTURE.md), [provjere faza](docs/DEVELOPMENT.md).
