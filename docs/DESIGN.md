# VoiceVault — studio di fattibilità

App Android per voice training MTF femminilizzante durante il commute in auto.
Documento di studio: **nessun codice scritto**, da validare prima dell'implementazione.

> Rev. 4 — la musica esce dallo scope: l'app non la gestisce.

---

## 1. Contesto d'uso (il vincolo che decide tutto)

| Fattore | Valore |
|---|---|
| Sessioni | 2/giorno, ~15 min l'una |
| Ambiente | abitacolo auto (auto vecchia, no Bluetooth), da sola, in guida |
| Hardware | telefono su supporto specchietto, **landscape** |
| Telefono | **Nothing Phone 3a** (Android 15 / Nothing OS 3, aggiornabile) |
| Audio in/out | **cuffie USB-C economiche con microfono inline, un solo auricolare indossato** |
| Musica | **fuori scope**: la gestisci tu con le tue app, VoiceVault non la riproduce |
| Archivio | si conserva tutto, senza cancellazione automatica |
| Mani | sul volante |
| Occhi | sulla strada |

Conseguenza progettuale centrale: **la sessione è guidata dall'audio, non dallo
schermo.** Lo schermo è un indicatore periferico, glanceable, mai un'interfaccia da
leggere o toccare. Il pitch tracker visivo del video (esercizio 3) in guida è
inutilizzabile: diventa **feedback sonoro** (§4).

Un auricolare solo è anche la scelta giusta rispetto al Codice della Strada, che
richiede di conservare adeguata capacità uditiva — non cito l'articolo a memoria,
ma la direzione è quella e l'app non deve spingere verso l'uso di entrambi.

---

## 2. Cosa cambia grazie alle cuffie USB-C (è una buona notizia)

Il passaggio da "microfono del telefono a 50 cm + Bluetooth dell'auto" a
"microfono inline a 3 cm + cavo" **elimina i due rischi principali** del progetto:

| Rischio della rev. 1 | Stato ora |
|---|---|
| Rumore di cabina a 65-75 dB sovrapposto alla f0 | **Risolto in gran parte.** Il mic inline sta a pochi centimetri dalla bocca: il rapporto segnale/rumore sale di 20-25 dB. Il pitch tracking diventa affidabile anche in autostrada. |
| Routing del mic rubato dal vivavoce dell'auto (HFP/SCO) | **Eliminato.** Nessun Bluetooth coinvolto. Il headset USB si presenta come `TYPE_USB_HEADSET` ed è input/output deterministico. |
| Drone di riferimento coperto dal rumore | **Risolto.** In cuffia il drone è pulito e l'intonazione percepibile. |
| Trigger di auto-start poco preciso | **Migliorato.** `AudioDeviceCallback` sull'aggiunta del dispositivo USB è un segnale netto: colleghi le cuffie → l'app propone la sessione. |

In compenso arrivano **due questioni nuove**, una delle quali è l'occasione più
interessante di tutto il progetto.

### 2a. L'effetto di occlusione (problema)
Con un auricolare in-ear senti la tua voce amplificata per conduzione ossea, con i
bassi esaltati. Percepisci la tua voce **più pesante di com'è davvero**. Per un
training basato esattamente sulla percezione del peso vocale, è una distorsione
sistematica: rischi di correggere un peso che non c'è, o di non sentire quello che c'è.

### 2b. Il sidetone (soluzione, e vero valore aggiunto)
Si reinvia il microfono nell'auricolare a basso volume e bassa latenza: senti la
tua voce **come la sente il microfono**, non come la senti dall'interno del cranio.
Per il voice training vale moltissimo — è il motivo per cui le cantanti usano gli
in-ear monitor. Diventa possibile solo perché hai scelto le cuffie.

Vincolo tecnico duro: serve **sotto i 25-30 ms** di latenza andata-e-ritorno.
Sopra, diventa un eco che destabilizza la voce ed è peggio di niente. Si fa con
Oboe/AAudio in low-latency mode, ma su USB Audio Class la latenza varia col
dispositivo: **va misurata, non assunta.** Se sul tuo setup non si scende sotto
soglia, il sidetone si disattiva e si tiene solo il drone. È il test #1 della Fase 0.

---

## 2bis. La musica (fuori scope)

Decisione: **VoiceVault non diventa un music player.** Ricadute, tutte positive
tranne una:

- **Cade il rischio principale della rev. 3.** Con le cuffie collegate la musica
  suona in cuffia, non nell'aria, quindi **non entra nel microfono**: il pitch
  detector non ha più f0 estranee a cui agganciarsi. Niente AEC da scrivere, niente
  gate acrobatici.
- **Cade il vincolo dei file locali**, quindi resta Spotify e resta "nessun permesso
  INTERNET" nel manifest. Si tiene tutto.
- **Niente routing per-player da verificare**: le cuffie USB sono l'unica uscita e
  basta. Il test #3 della Fase 0 sparisce.
- **L'unica cosa che perdi è l'effetto disinibente** della musica nell'aria, perché
  finisce nell'orecchio insieme al drone invece di stare nell'ambiente.

Quello che l'app fa, e sono poche righe: richiede l'audio focus in modalità
`TRANSIENT_MAY_DUCK`, così qualunque cosa stia suonando **si abbassa da sola**
durante i prompt e gli esercizi e torna su nelle pause. Funziona con Spotify o con
qualsiasi altro player, senza integrazioni.

Se più avanti l'imbarazzo torna a essere un ostacolo, la sostituzione naturale non è
la musica ma un **rumore di mascheramento tenue in cuffia**: riduce l'ascolto di sé
con lo stesso effetto psicologico, senza interferire col drone né col microfono.
Una riga di codice, da provare quando serve.

---

## 3. Principi di design

1. **Eyes-free first.** Ogni informazione necessaria arriva via audio. Lo schermo
   mostra uno stato a colpo d'occhio, leggibile a un metro.
2. **Zero tocchi in marcia.** Colleghi le cuffie, l'app propone la sessione con
   auto-start. Il resto si fa a motore spento.
3. **Dark mode permanente, AMOLED-friendly.** Nessun bianco, luminanza bassa,
   nessuna animazione rapida in visione periferica.
4. **Tutto locale.** Nessun account, nessun backend, **nessun permesso INTERNET**
   nel manifest.
5. **Mono, sempre.** Indossi un solo auricolare: tutto l'audio generato dall'app è
   downmixato in mono e duplicato sui due canali. Se sbagli questo dettaglio, metà
   del feedback finisce nell'orecchio che non hai in testa.
6. **Semplice.** Un flusso, un archivio, poche metriche oneste.

---

## 4. Il feedback audio (sostituisce il pitch tracker visivo)

- **Drone di riferimento**: tono tenue continuo sulla nota target (es. ~196 Hz come
  zona di partenza, poi calibrato su di te). Senti se sei sopra o sotto dal
  battimento acustico — più preciso di qualsiasi numero sullo schermo.
- **Sonificazione del peso vocale**: un proxy del peso (§7) pilota un filtro sul
  drone: voce pesante → drone ruvido; voce leggera → drone pulito. Impari a cercare
  il suono leggero a orecchie.
- **Sidetone** (se la latenza lo permette, §2b): la tua voce reale, a basso volume,
  sotto il drone.
- **Conferme brevi**: tick discreto quando resti in zona target per 2 s. Nessuna
  voce che commenta in continuo: distrae e stufa.
- Lo schermo replica la stessa informazione in colore (ambra = pesante, verde-azzurro
  = leggera; posizione verticale = altezza). Visione periferica, non lettura.

---

## 5. Flusso utente

**Collegamento cuffie (auto ferma o appena partita)**
- Schermata/notifica: *"Sessione A — 12 min. Tocca o aspetta."* Countdown 10 s e
  auto-start, così in marcia non serve fare niente.

**Durante (eyes-free)**
- Voce guida (TTS) che annuncia esercizio e cambi. La musica si abbassa (ducking)
  durante i prompt e sotto il drone, e torna su nelle pause.
- Schermo: nome esercizio in grande + barra/alone di colore + tempo rimanente. Nulla
  di più.
- Gesti: **tap in qualunque punto = avanti**, **doppio tap = ripeti**,
  **long press = stop**. Nessun bersaglio piccolo, mai.
- Registrazione continua con marker temporali per esercizio (foreground service).

**Post-guida (parcheggiata)**
- "Com'è andata": grafico pitch della sessione, segmenti migliori, 1-2 insight,
  pulsanti grossi. Nessuna scelta obbligatoria: tutto è già salvato.
- Calendario mensile con i giorni allenati.

---

## 6. Gli esercizi (dal materiale TransVoiceLessons)

Ogni esercizio è un blocco dati (nome, durata, prompt vocale, tipo di feedback,
metrica da loggare). Il programma è generato, non hardcoded in una schermata.

| # | Esercizio | Durata | Feedback |
|---|---|---|---|
| 0 | Riscaldamento: hum rilassato, respiro | 2 min | drone, nessuna metrica |
| 1 | **Hum & slide** pesante basso → leggero alto | 3 min | drone + sonificazione peso |
| 2 | **Heavy-heavy-light**: due colpi pesanti in salita, terzo alto e leggero | 3 min | tick sul terzo colpo se leggero |
| 3 | **Esplorazione del limite** (ex pitch tracker): sali fino al "blocco", rilassa, supera, riscendi leggera | 3 min | drone mobile che ti accompagna |
| 4 | **Vocal fry → pesante → leggero** (bonus) | 2 min | sonificazione peso |
| 5 | **Carryover al parlato**: conta 1-2-3-4, poi frasi semplici | 2 min | pitch medio + % tempo in target |

Totale **12-13 min**, dentro i 15 di guida con margine per traffico e semafori. Due
programmi alternati (A: 1-2-5, B: 3-4-5) così le due sessioni del giorno non sono
identiche e non si sfinisce la voce. L'app non supera mai ~13 min per sessione e
propone pausa se rileva spinta sostenuta (volume alto + pitch alto a lungo).

---

## 7. Fattibilità tecnica

### Tutto on-device, tutto standard
- **Registrazione**: `AudioRecord`, 44.1 kHz mono PCM per l'analisi. Sorgente
  `UNPROCESSED` se l'hardware la dichiara, altrimenti `VOICE_RECOGNITION`; **mai
  `VOICE_COMMUNICATION`** (AGC ed echo cancel falsano ampiezza e spettro, cioè
  proprio le metriche di peso). Input forzato su `TYPE_USB_HEADSET` con
  `setPreferredDevice`.
- **Pitch detection**: YIN o autocorrelazione normalizzata, finestre di 40 ms, con
  soglia di confidenza. Costo trascurabile. ~200 righe di Kotlin, zero dipendenze
  (alternativa: TarsosDSP).
- **Proxy del peso vocale**: *spectral tilt* (energia <1 kHz vs >1 kHz) + HNR +
  H1-H2. Correla bene con la percezione di voce densa/ronzante. Calibrato sulla tua
  voce nelle prime sessioni, non su soglie assolute.
- **Audio a bassa latenza**: Oboe/AAudio per drone, sonificazione e sidetone, su
  thread audio dedicato.
- **Background**: foreground service con `FOREGROUND_SERVICE_MICROPHONE`
  (obbligatorio da Android 14), notifica persistente, schermo che può spegnersi.
- **Stack**: Kotlin + Jetpack Compose, single module, minSdk 29. Room per dati.
  Nessuna libreria cloud, nessuna analytics.

### Punti ancora aperti
1. **Latenza USB Audio Class** → decide se il sidetone è possibile (§2b). Test #1.
2. **Processing dentro il DAC delle cuffie.** Alcuni headset USB-C fanno AGC/noise
   suppression nel loro chip, fuori dal controllo di Android. Non è aggirabile: se
   succede, le metriche di peso vanno calibrate su quel comportamento. Test #2.
3. **Cuffie economiche, modello sconosciuto.** È la variabile meno controllabile:
   se il loro chip fa AGC o noise suppression, le metriche di peso vocale vanno
   calibrate su quel comportamento perché non è disattivabile. Se lo spike mostra
   processing aggressivo o latenza alta, la spesa più utile del progetto è un
   headset USB-C decente — non più software.
4. **Navigatore**: Maps deve poter parlare sopra tutto, con pausa e ripresa pulita
   della sessione.
4. **Alimentazione**: la USB-C è occupata dalle cuffie, quindi non carichi mentre ti
   alleni. 30 min/giorno di registrazione + audio sono gestibili, ma se il supporto
   specchietto prevedeva la ricarica, serve un adattatore con passthrough.
5. **Rumore residuo**: con il mic a 3 cm resta solo il caso limite (finestrino
   aperto, autostrada). Si logga la qualità del segnale e si marca la sessione, invece
   di presentare numeri inaffidabili come se fossero buoni.

---

## 8. Archivio (hai scelto: tieni tutto per sempre)

**Dimensioni reali**, da dire chiaramente: 2 sessioni da 15 min = 30 min/giorno.

| Codec | Al giorno | All'anno |
|---|---|---|
| Opus 32 kbps mono | ~7 MB | ~2,6 GB |
| **Opus 48 kbps mono (proposta)** | **~11 MB** | **~4 GB** |
| Opus 64 kbps mono | ~14 MB | ~5,2 GB |

Proposta: **Opus 48 kbps mono**. Con un microfono a 3 cm la voce è ottima e resta
riascoltabile e rianalizzabile in futuro. L'app mostra lo spazio occupato e avvisa
sopra una soglia che decidi tu, senza mai cancellare da sola.

**Il vero rischio di "per sempre" non è lo spazio: è perdere il telefono.** E qui
c'è una tensione con il principio "nessuna rete": il backup automatico di Android
caricherebbe l'archivio su Google Drive, cioè la tua voce su un server. Proposta:
`allowBackup=false` + archivio in una cartella visibile (`Documents/VoiceVault/`)
così lo copi via cavo quando vuoi, più una funzione *Esporta* che prepara un unico
zip. Backup consapevole e manuale invece che automatico e invisibile.

---

## 9. Calendario e insight ("stiamo sul semplice")

**Calendario**: griglia mensile, un pallino per giorno (pieno = 2 sessioni, mezzo =
1), streak corrente, minuti totali del mese. Tap su un giorno → sessioni con grafico
e audio riascoltabile.

**Insight**: regole deterministiche, niente ML, massimo 2 card per volta. Esempi:
- "Negli ultimi 7 giorni il pitch medio nel parlato è salito da 142 a 156 Hz."
- "Nell'esercizio 2 arrivi alta ma il peso resta alto: prova a partire più piano."
- "Le sessioni del mattino hanno range più stretto — forse serve più riscaldamento."
- "Hai raggiunto 210 Hz leggera per la prima volta."
- "3 giorni senza sessione: ripartiamo dal programma A, versione corta."

Metriche per sessione, poche e oneste: f0 mediana, f0 min/max affidabili, % tempo in
zona target, indice di peso medio, qualità del segnale, durata.

---

## 10. Cosa NON fare (scope control)

- Niente analisi di risonanza/formanti nella v1: è il passo successivo del percorso
  (lo dice anche il video) e raddoppia la complessità di segnale.
- Niente cloud, social, condivisione, account.
- Niente riconoscimento vocale complesso.
- Niente grafici in tempo reale in guida.
- Niente notifiche motivazionali insistenti.

---

## 11. Fasi

**Fase 0 — spike di rischio (un paio d'ore).** App minima che risponde a tre
domande sulle tue cuffie economiche:
1. **Latenza** andata-e-ritorno: sotto 30 ms → sidetone possibile, sopra → si tiene
   solo il drone.
2. **Processing del DAC**: alterano guadagno e spettro? Decide la calibrazione delle
   metriche di peso.
3. **Qualità del pitch tracking in auto in marcia**, con il mic a 3 cm e senza musica
   addosso: deve essere la parte facile, va confermato.

**Fase 1 — MVP usabile.** Sessione guidata (TTS) con 3 esercizi, drone di
riferimento, schermo glanceable, registrazione, calendario con pallini.

**Fase 2 — feedback fine.** Sidetone (se la Fase 0 lo permette), sonificazione del
peso, tutti e 5 gli esercizi, programmi A/B, review post-sessione con grafico.

**Fase 3 — rifiniture.** Insight, auto-start su collegamento cuffie, export/backup,
lock biometrico, gestione spazio.

---

## 12. Domande ancora aperte

1. Hai già un pitch di riferimento/target, o deve stabilirlo l'app nelle prime
   sessioni? (è l'unica cosa che serve per iniziare la Fase 1)
2. Discrezione: nome e icona neutri? lock biometrico all'apertura?
