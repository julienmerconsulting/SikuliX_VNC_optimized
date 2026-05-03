# CLAUDE.md — Branche `feat/reporter-module`

> Fichier de trace et de reprise pour les sessions Claude Code.
> **Objectif** : toute nouvelle session peut lire ce fichier et reprendre le
> travail sans explorer/re-comprendre. Économise des tokens Opus.

## Contexte du projet

OculiX = fork Java de SikuliX (automatisation visuelle via OpenCV). Le module
`Reporter/` est un nouveau module **indépendant** qui génère un rapport HTML
standalone + exports JUnit XML / JSON pour les tests OculiX, Selenium, TestNG
et JUnit 5.

**Référence qualité** : le reporter pytest `pytest-translate` (2670 lignes
Python, 14 modules) hébergé sur la branche `idea/feature_reporter` dans
`report.zip`. Le Java doit atteindre un rendu **équivalent ou meilleur**.

## Règle d'or : zéro modification du code existant

Le module Reporter **ne doit toucher aucune classe existante** d'OculiX.
Seule exception autorisée : ajouter `<module>Reporter</module>` dans le
`pom.xml` racine.

Mécanismes pour rester découplé :
- `ReportedScreen extends org.sikuli.script.Screen` — wrapper opt-in,
  l'utilisateur substitue `new Screen()` par `new ReportedScreen()` dans
  ses tests. Les Screen existantes ne sont pas touchées.
- **Mode ambient** : `ReportedScreen()` sans argument lit le test courant
  via `OculixReporter.currentTest()` (ThreadLocal). Zéro glue chez
  l'utilisateur quand il pilote via JUnit 5 / TestNG.
- Listeners JUnit 5 / TestNG / Selenium = plugins activés explicitement
  par annotation ou `setUp()`. Jamais de modification core.

## État des phases

| Phase | Statut      | Commits                          |
|-------|-------------|----------------------------------|
| 1 — MVP (data model + HTML + wrapper + mock demo) | **DONE** | `8c9e4b0`, `370ffa9`, `576a249` |
| 2 — Donut + Timeline SVG                          | **WIP**  | `30c28a0` (classes créées, pas câblées dans HtmlRenderer) |
| 3 — Diagnosis offline                             | TODO     | — |
| 4 — History + flaky detection                     | TODO     | — |
| 5 — Exports JUnit XML + JSON                      | partiellement couvert par Phase 5 listeners (voir ci-dessous) |
| Phase 5 listeners (TestNG + JUnit 5 + Selenium 3/4) | **DONE** | `353836b`, `785d10e` |
| Killer — Selenium EventListener                   | listener basique livré en `353836b`, pas de polish |

## Inventaire des fichiers Reporter

```
Reporter/
├── pom.xml                         # aligné style API/pom.xml, deps optionnelles
└── src/
    ├── main/
    │   ├── java/org/oculix/report/
    │   │   ├── OculixReporter.java         # façade static, ThreadLocal<Test>
    │   │   ├── ReportedScreen.java         # wrapper Screen, mode ambient
    │   │   ├── model/
    │   │   │   ├── Outcome.java            # enum 6 valeurs + couleurs
    │   │   │   ├── Screenshot.java         # data URI + caption
    │   │   │   ├── Step.java               # action/target/duration/screenshots
    │   │   │   ├── Test.java               # worst-wins outcome resolution
    │   │   │   └── TestRun.java            # top-level + counts + successRate
    │   │   ├── render/
    │   │   │   ├── HtmlRenderer.java       # MAIN renderer (à étendre Phase 2)
    │   │   │   ├── Donut.java              # SVG stroke-dasharray (PAS CÂBLÉ)
    │   │   │   └── Timeline.java           # Gantt SVG (PAS CÂBLÉ)
    │   │   ├── junit5/OculixJUnit5Extension.java
    │   │   ├── testng/OculixTestNGListener.java
    │   │   ├── selenium/
    │   │   │   ├── SeleniumReportingListener.java
    │   │   │   └── SeleniumWrap.java       # dispatcher Selenium 3/4 via reflect
    │   │   └── mock/MockReportDemo.java    # main() — 9 faux tests pour valider visuel
    │   └── resources/org/oculix/report/
    │       ├── reporter.css                # 377 lignes, palette pytest-translate
    │       └── reporter.js                 # 52 lignes, filtres sidebar + toggle
    └── test/
        └── java/org/oculix/report/
            └── OculixJUnit5IntegrationTest.java  # 3 tests passed/failed/skipped
```

## Comment lancer / valider visuellement

```bash
# Compiler le module
cd Reporter && mvn -q -DskipTests package

# Générer le rapport mock (primary deliverable pour itérer UX)
mvn -q exec:java -Dexec.mainClass=org.oculix.report.mock.MockReportDemo
# → produit Reporter/target/demo-report.html à ouvrir dans un navigateur

# Lancer le test d'intégration JUnit 5 (preuve Phase 5 end-to-end)
mvn -q test
# → produit Reporter/target/oculix-integration-report.html
```

## Plan d'exécution — Phase 2 (Donut + Timeline SVG)

**But** : câbler `Donut.java` et `Timeline.java` dans `HtmlRenderer.java`
pour afficher en haut du rapport une visualisation graphique des outcomes
(donut) et une frise chronologique des tests (Gantt).

### Étape 1 — HtmlRenderer : nouveau bloc "overview"

Dans `HtmlRenderer.render()`, entre `renderHeader()` et `renderTiles()`,
ajouter un appel `renderOverview(sb, run)` qui pond :

```html
<section class="overview">
  <div class="overview-donut">
    <svg>...</svg>            ← Donut.generate(run.counts(), 220)
    <ul class="donut-legend">...</ul>
  </div>
  <div class="overview-timeline">
    <h4>Timeline</h4>
    <svg>...</svg>            ← Timeline.generate(run)
  </div>
</section>
```

- Legend : boucle sur `Outcome.values()`, skip les 0, `<li>` avec pastille
  couleur + label + count.
- Si `Timeline.generate(run)` retourne `""` (pas de timing), ne pas rendre
  la colonne timeline.

### Étape 2 — CSS `reporter.css` : styles overview

Ajouter en fin de fichier (juste avant `/* ========== Responsive ========== */`
si la section existe, sinon en fin de fichier) :

```css
/* ========== Overview (donut + timeline) ========== */
.overview {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 1.5rem;
  margin: 1.5rem 0 2rem;
  padding: 1.5rem;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
}
.overview-donut { display: flex; flex-direction: column; align-items: center; }
.overview-donut svg { width: 220px; height: 220px; }
.donut-legend { list-style: none; padding: 0; margin-top: 1rem; display: flex; flex-wrap: wrap; gap: .75rem; justify-content: center; }
.donut-legend li { display: flex; align-items: center; gap: .4rem; font-size: .85rem; color: var(--fg-soft); }
.donut-legend .swatch { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.overview-timeline h4 { margin: 0 0 .5rem; font-size: .85rem; color: var(--muted); font-weight: 600; text-transform: uppercase; letter-spacing: .05em; }
.overview-timeline svg { width: 100%; height: auto; }
@media (max-width: 900px) {
  .overview { grid-template-columns: 1fr; }
}
```

### Étape 3 — Donut : ajouter libellé central (total)

Dans `Donut.generate()`, avant la balise fermante `</svg>`, insérer :

```java
sb.append(String.format(
  "<text x=\"%.2f\" y=\"%.2f\" text-anchor=\"middle\" dominant-baseline=\"central\" " +
    "font-size=\"%.0f\" fill=\"currentColor\" font-weight=\"700\">%d</text>\n",
  cx, cy - size*0.04, size*0.22, total));
sb.append(String.format(
  "<text x=\"%.2f\" y=\"%.2f\" text-anchor=\"middle\" dominant-baseline=\"central\" " +
    "font-size=\"%.0f\" fill=\"currentColor\" fill-opacity=\"0.6\">tests</text>\n",
  cx, cy + size*0.10, size*0.06));
```

Donne le gros chiffre au centre du donut + label "tests" en dessous, comme
le donut pytest-translate.

### Étape 4 — MockReportDemo : vérifier qu'il génère bien du timing

Lire `MockReportDemo.java`. S'assurer que chaque `Test` reçoit bien
`startedAt()` / `endedAt()` espacés (sinon `Timeline.generate()` retourne
"" et la colonne timeline ne s'affiche pas). Si non, ajouter des
`Instant.plusSeconds()` croissants entre les tests.

### Étape 5 — Build + visualisation

```bash
cd Reporter && mvn -q -DskipTests package exec:java -Dexec.mainClass=org.oculix.report.mock.MockReportDemo
# ouvrir Reporter/target/demo-report.html dans navigateur
# vérifier : donut visible avec chiffre central, timeline visible avec
# barres colorées alignées, bascule dark/light via système OK
```

### Étape 6 — Commit

```bash
git add Reporter/src/main/java/org/oculix/report/render/HtmlRenderer.java \
        Reporter/src/main/java/org/oculix/report/render/Donut.java \
        Reporter/src/main/resources/org/oculix/report/reporter.css
git commit -m "Reporter: Phase 2 — wire Donut + Timeline into HtmlRenderer + overview CSS"
git push -u origin feat/reporter-module
```

**Estimation** : ~30-40 min d'Opus en une session dédiée, sans exploration
(tout est déjà ici).

## Plans résumés Phase 3 / 4 / Killer

### Phase 3 — Diagnosis offline (~1h30 Opus)

Port de `diagnosis.py` (257 lignes, 36 règles regex) en Java.

- Créer `Reporter/src/main/java/org/oculix/report/diagnosis/`
  - `Diagnosis.java` (data class : category, severity, hint, matchedText)
  - `DiagnosisEngine.java` (applique règles ordonnées sur stackTrace + errorMessage)
  - `DiagnosisRules.java` (catalogue des règles, avec règles spécifiques OculiX :
    `FindFailed`, `UnsatisfiedLinkError: opencv_java`, `ImageNotFound`)
- Étendre `Test` pour porter un `Optional<Diagnosis> diagnosis`
- Dans `HtmlRenderer.renderTest()`, si `diagnosis.isPresent()`, afficher un
  bandeau coloré par catégorie (violet pour `pattern_not_found`, rouge pour
  `assertion`, orange pour `network`, etc.) avec le hint.
- CSS classes `.diag-banner .diag-<category>` — 6-8 variantes couleur.

### Phase 4 — History + flaky detection (~1h Opus)

Port de `history.py` (172 lignes).

- `Reporter/src/main/java/org/oculix/report/history/`
  - `HistoryStore.java` (lecture/écriture `.oculix/history.json`, 20 runs max)
  - `HistoryEntry.java` (runId, timestamp, outcomes par test)
  - `FlakyDetector.java` (score = nb flips outcome / nb runs)
- `HtmlRenderer` : nouvelle section "Previous run" avec diff count
  (X regressions, Y new passes, Z flaky). Sparkline SVG inline pour chaque
  test montrant les N derniers outcomes.
- JSON via `java.util.Properties` non, plutôt via une sérialisation manuelle
  à la main (pas de Jackson, on garde zéro-dépendance). Format compact, lisible.

### Phase 5 restante — Exports JUnit XML + JSON (~45min Opus)

- `Reporter/src/main/java/org/oculix/report/export/`
  - `JUnitXmlRenderer.java` — format `<testsuite><testcase>` standard Ant/Surefire
    compatible Xray, Zephyr, TestRail. Pas de DTD spécifique, le format "JUnit"
    de base marche partout.
  - `JsonRenderer.java` — format QA OPS LAB (à définir, s'inspirer du JSON
    de pytest-translate).
- Étendre `OculixReporter.writeTo(Path)` pour générer les 3 outputs en parallèle
  (HTML, XML, JSON) si extensions connues.

### Killer — Selenium EventListener polish (~1-2h Opus)

Le basique est dans `selenium/SeleniumReportingListener.java`. Reste à :
- Documenter les 2 modes d'install (Selenium 3 `EventFiringWebDriver.register()`
  vs Selenium 4 `WebDriverListener`)
- Tester en vrai avec un WebDriver réel (Chrome headless). Le dispatch
  reflective doit tenir les 2 versions.
- Exemple de code dans README Reporter (pas encore de README).

## Conventions observées sur cette branche

- **Commits préfixés** `Reporter:` pour tous les commits du module
- Messages courts, descriptifs (une ligne, parfois bullet dans le body)
- **Pas de co-author Claude** dans les messages (pas demandé)
- **Pas de pre-commit hooks** actifs sur ce repo (Maven standalone)
- Branche upstream : `origin/feat/reporter-module`
- Merge cible : `master` (oculix-org/Oculix)

## Budget token — conseils d'économie

- Ne **pas** re-lire les fichiers déjà inventoriés ci-dessus au démarrage
- Se baser sur ce `CLAUDE.md` pour la reprise, pas sur du `git log` + `ls`
- Les fichiers `HtmlRenderer.java`, `reporter.css`, `MockReportDemo.java`
  font 200-400 lignes chacun → chaque Read coûte. Ne les relire que si on
  les modifie.
- Screenshots = chers. Préférer des descriptions textuelles.
- Utiliser `/compact` avant de commencer une grosse phase pour purger
  l'historique inutile.

## Dernière mise à jour

2026-04-24 — après Phase 1 + Phase 5 commités, Phase 2 WIP (classes SVG
créées, câblage restant), plan d'exécution Phase 2 détaillé ajouté ici.
