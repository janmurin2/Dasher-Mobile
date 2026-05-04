# Dasher Mobile
Dasher Mobile is an Android implementation of the [Dasher](https://www.inference.org.uk/dasher/) probabilistic text input system, designed for efficient and accessible text entry using touch gestures or device tilt.

---

# INFORMÁCIE PRE TESTEROV APLIKÁCIE

Testovanie prebieha najskôr inštaláciou aplikácie a krátkym oboznámením sa s fungovaním klávesnice Dasher a následne dvanástimi blokmi po 5 minút, počas ktorých budeš prepisovať vopred nachystaný text.

**Cutoff time je do piatku 8.5.2026 12:00, potom začnem vyhodnocovať výsledky.**

---

## Inštalácia

1. Nainštaluj si aplikáciu stiahnutím súboru `DasherMobile-v1.0-release.apk` z https://github.com/janmurin2/Dasher-Mobile/releases/tag/v1.0
   - V nastaveniach povoľ inštaláciu aplikácie z neznámych zdrojov.
     <p align="center">
      <img width="270" hspace="20" alt="install" src="https://github.com/user-attachments/assets/c139e7db-070c-456a-9315-121ec4f18857" />
      <img width="270" hspace="20" alt="app_scan" src="https://github.com/user-attachments/assets/0d947b57-857a-4441-8601-c1b623fcaf70" />
    </p>
   - Po inštalácii sa aplikácia bude zobrazovať pod názvom **Dasher Mobile**.

2. Stiahni si súbor `kenlm_Slovak.binary` z https://github.com/janmurin2/Dasher-Mobile/releases/tag/v1.0

3. Otvor aplikáciu **Dasher Mobile** a v pravom hornom rohu sa preklikni na obrazovku nastavení.
   - Ako **language** vyber `Slovak`
   - Ako **language model** vyber `KenLM`
   - Na spodku obrazovky sa zobrazí upozornenie, že sa nenašiel KenLM súbor pre zvolený jazyk, a tlačidlo na importovanie súboru.
   - Klikni na **Import file** a vyber súbor `kenlm_Slovak.binary`.

4. Hotovo

---

## Oboznámenie sa s klávesnicou Dasher

### Čo je Dasher?

Dasher je nekonvenčný systém zadávania textu, ktorý využíva plynulú navigáciu v priestore písmen. Celá abeceda je zobrazená ako stĺpec na pravom okraji obrazovky a ty do jednotlivých znakov vrážaš, buď dotykom, alebo náklonom zariadenia. Systém využíva jazykový model na predpovedanie pravdepodobnosti jednotlivých písmen a podľa toho priraďuje väčší priestor tým písmenám, ktoré s najväčšou pravdepodobnosťou nasledujú. Časté kombinácie písmen sú teda väčšie a je lahšie sa do nich trafiť. Pre príklad písania si pozri [Demonstration of Typing with Dasher](https://www.youtube.com/watch?v=nr3s4613DX8).

<p align="center">
  <img width="270" hspace="20" alt="ahoj" src="https://github.com/user-attachments/assets/1c99cb52-9558-4488-84dd-e7f04da7735d" />
  <img width="270" hspace="20" alt="settings" src="https://github.com/user-attachments/assets/909eedc1-57da-40bd-8d97-ea6d57791906" />
</p>

### Popis ovládania

Dasher Mobile podporuje dva režimy ovládania:

#### Dotykový režim (Touch)
- Priložením prstu na obrazovku spustíš pohyb, Dasher sa začne posúvať v smere tvojho dotyku.
- Poloha prstu určuje smer **vyššie = pohyb nahor**, **nižšie = pohyb nadol**, pričom horizontálna os kontroluje rýchlosť.
- Zdvihnutím prstu sa pohyb **zastaví (pauza)**.
- Opätovným dotykom pokračuješ v písaní.

#### Režim náklonu (Tilt)
- Pohyb ovládaš **náklonom zariadenia**.
- Náklon dopredu/dozadu ovláda vertikálnu polohu (výber písmena), náklon doľava/doprava ovláda rýchlosť pohybu vpred/vzad.
- Po prepnutí do tilt režimu je potrebné **kalibrovať** štartovaciu pozíciu tlačidlom **Calibrate**.
- Dotyk obrazovky v tilt režime pohyb **pozastaví** znovu je ho možné spustiť stlačením tlačidla **Calibrate**.


### Užitočné informácie
- Znaky sú rozdelené do štyroch kategórií: **malé písmená**, **veľké písmená**, **interpunkcia**, **medzera**.
- Všetky znaky sú zoradené podľa abecedy – „a" je na vrchu a „z" na spodku kategórie.
- Symbol pre medzeru je **□**.
- Ak urobíš chybu, pohybom **dozadu** (doľava) sa vrátiš a vymažeš posledné napísané znaky.

---

## Testovanie

1. Nainštaluj si aplikáciu podľa návodu vyššie a prečítaj si odsek na oboznámenie sa s Dasher klávesnicou.

2. Vyhrať si čas na **dvanásť 5-minútových blokov** testovania. Nemusí to byť naraz, pokojne aj v priebehu viacerých dní, ale najneskôr do piatku 8.5.2026 12:00. Mimo týchto testovacích blokov prosím aplikáciu nepoužívaj, budem vyhodnocovať aj postupné zlepšenie rýchlosti a presnosti písania.
   - Nastav si časovač na 5 minút.
   - Otvor si na inom zariadení text na prepisovanie, prípadne si ho vytlač. Texty nájdeš [tu](https://github.com/janmurin2/Dasher-Mobile/tree/master/docs/sampled_sentences_sk). Vyber text s číslom podľa poradia testovacieho bloku.
   - V nastaveniach vyber 
	    - language: Slovak
	    - language model: KenLM
	    - input mode: Tilt
	    - movement speed: nastav podľa seba, ale odporúčam na začiatok hodnotu 100% 
   - Spusti časovač a začni prepisovať text.
   - Po uplynutí 5 minút skopíruj napísaný text.
   - Prilep skopírovaný text do tela mailu, ktorý pošleš na adresu **janko.murin.bb@gmail.com**. Ako predmet napíš, v poradí koľký blok testovania je toto, a zvolenú rýchlosť v nastaveniach (movement speed).

3. Napíš mi prosím krátku **spätnú väzbu** do mailu **janko.murin.bb@gmail.com**:
   - Pocity z používania Dasher klávesnice?
   - Preferuješ ovládanie Dasheru náklonom zariadenia alebo dotykom?
   - Tvoje nápady na zlepšenie.

4. Hotovo. Ďakujem! Ak sa ti aplikácia páčila, môžeš si ju v nastaveniach vybrať ako predvolenú klávesnicu. V takom prípade jej veľkosť vieš zmeniť nastavením hodnoty **ime size** v nastaveniach Dasher Mobile, rovnako ako aj spôsob ovládania, jazyk, jazykový model a rýchlosť. 

<p align="center">
  <img width="270" hspace="20" alt="deafult_keyboard" src="https://github.com/user-attachments/assets/e82b610f-d4ce-4c97-a74b-e12a33d87fee" />
  <img width="270" hspace="20" alt="dasher_notes" src="https://github.com/user-attachments/assets/47cbadd8-11a5-4954-afad-bf83a8610ea3" />
</p>

---

V prípade nejasností alebo problémov s aplikáciou ma prosím kontaktuj na **janko.murin.bb@gmail.com**.
