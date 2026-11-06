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

## Cosa c'è in questa fase

- registrazione in foreground service, AAC-LC mono in `.m4a`;
- **drone di riferimento** con glide morbido, mono duplicato sui due canali;
- **pitch detection YIN** con soglia di confidenza: i frame inaffidabili si
  scartano invece di produrre numeri inventati;
- proxy del **peso vocale** (inclinazione spettrale, H1-H2) misurato e registrato;
- sessione guidata dagli **esempi audio della lezione**, ritagliati e inclusi
  nell'app: riscaldamento, hum e scivolata, pesante-pesante-leggero, carryover al
  parlato. Mentre un esempio suona il drone tace. La voce sintetica resta solo
  dove un esempio non c'è;
- **calibrazione**: 90 secondi in testa alla prima sessione misurano la tua voce
  (mediana del parlato e tetto comodo) e tarano il target del drone, invece di
  prenderlo da una tabella. Il target entra in vigore subito, a metà sessione, e
  si può chiedere una **ricalibrazione** quando la voce si è spostata;
- schermata di guida landscape: tap in qualunque punto = avanti, pressione lunga
  = stop. Nessun bersaglio piccolo;
- calendario mensile con i pallini, striscia di giorni, minuti del mese;
- insight da regole deterministiche, al massimo due per volta;
- **registrazioni** elencate e riascoltabili, salvate nella cartella pubblica
  `Registrazioni/VoiceVault`;
- **diagnostica** per misurare i due rischi residui (compressione in ingresso
  delle cuffie, qualità del pitch tracking in marcia) e per leggere l'ultimo
  errore, dato che in auto non c'è un cavo per i log di sistema.

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
