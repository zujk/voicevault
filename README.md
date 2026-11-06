# VoiceVault

App Android per voice training MTF femminilizzante da usare in auto, durante il
commute, a mani sul volante e occhi sulla strada.

Lo studio di fattibilità completo, con le decisioni e i motivi, sta in
[`docs/DESIGN.md`](docs/DESIGN.md). Questa è la Fase 1 di quel documento.

## L'idea in tre righe

La sessione è guidata dall'**audio**, non dallo schermo: una voce annuncia gli
esercizi e un **drone di riferimento** continuo ti dà la nota target a orecchio.
Lo schermo è solo una spia periferica, e durante la guida non serve toccarlo.
Gli esercizi vengono dal materiale di TransVoiceLessons per principianti: il
punto non è alzare l'altezza, è imparare a ridurre il **peso vocale** salendo.

## Setup previsto

| | |
|---|---|
| Telefono | Nothing Phone 3a, su supporto allo specchietto, landscape |
| Audio | cuffie USB-C con microfono inline, **un solo auricolare** indossato |
| Sessione | 12 minuti circa, due volte al giorno |
| Musica | la gestisci tu: l'app si limita ad abbassarla durante i prompt |

Il microfono a pochi centimetri dalla bocca è la ragione per cui le metriche in
auto stanno in piedi: l'app lo seleziona esplicitamente invece di affidarsi al
routing automatico.

## Cosa c'è

**Una sessione è: drone, registrazione, cronometro.** Niente altro.

- **drone di riferimento** sulla tua nota, con glide morbido, mono duplicato sui
  due canali. Due pulsanti enormi lo spostano di un semitono, in marcia o da fermi;
- **registrazione** in foreground service, AAC-LC mono in `.m4a`, nella cartella
  pubblica `Registrazioni/VoiceVault`;
- **pitch detection YIN** con soglia di confidenza: i frame inaffidabili si
  scartano invece di produrre numeri inventati;
- proxy del **peso vocale** (inclinazione spettrale, H1-H2) misurato e registrato;
- schermata di guida landscape: cronometro, Hz del drone, Hz della tua voce, e la
  colonna del pitch colorata sul peso. Tre bersagli grandi: −, +, FINE;
- **calendario** mensile con i pallini, striscia di giorni, minuti del mese;
- **lezione** spacchettata in 13 clip raggruppate per tipo, da ascoltare da fermi;
- **registrazioni** elencate e riascoltabili;
- **insight** da regole deterministiche, al massimo due per volta;
- **diagnostica** per i due rischi residui (compressione in ingresso delle cuffie,
  qualità del pitch tracking in marcia) e per leggere l'ultimo errore, dato che in
  auto non c'è un cavo per i log di sistema.

### Cosa è stato tolto, e perché

C'erano quattro esercizi con timer, una voce sintetica che li annunciava e le clip
della lezione che partivano da sole. In un orecchio solo potevano esserci cinque
sorgenti audio insieme — musica, drone, clip, voce sintetica, navigatore — e
ognuna doveva spegnerne un'altra con regole invisibili: quando qualcosa non si
sentiva era impossibile capire se fosse rotto o zittito di proposito. Più una
macchina a stati da tenere a mente mentre si guida.

Via tutto: il drone suona, l'app registra, e cosa fare con la voce lo decide chi
guida. Il materiale di studio si ascolta da fermi, nella schermata Lezione.

Con gli esercizi è sparita anche la calibrazione automatica: la nota del drone si
sposta a mano di un semitono per volta, e un insight fa notare quando la tua
mediana l'ha raggiunta.

## Privacy

Nel manifest **non c'è il permesso INTERNET**: l'app non può spedire niente da
nessuna parte, nemmeno per sbaglio. Il backup automatico su cloud è disattivato,
perché caricherebbe le registrazioni della voce su un server. Il backup è
manuale, via cavo.

## Build

Serve Android Studio (o l'Android SDK con `ANDROID_HOME` impostato):

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:installDebug
```

## Due scostamenti dal documento di design

1. **AAC-LC a 64 kbps invece di Opus a 48**: l'encoder Opus di Android non è
   garantito su tutti i dispositivi, AAC-LC sì. Costo: ~14 MB al giorno invece di
   ~11, cioè ~5 GB all'anno.
2. **Niente Oboe/NDK**: serviva per il sidetone, che è rinviato alla Fase 3. Il
   drone è pura riproduzione e `AudioTrack` in modalità bassa latenza basta.

## Stato

Scritta ma **mai compilata né provata su un telefono**: l'ambiente in cui è nata
non ha l'Android SDK. Il primo passo è aprirla in Android Studio, sistemare quello
che la compilazione segnala, e portare la diagnostica in auto.
