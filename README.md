# Bake It Off 🧁

App Android (Kotlin + Jetpack Compose) que transforma vídeos, imagens ou links
compartilhados (TikTok, Instagram, etc.) em receitas estruturadas usando IA
(Gemini), e organiza tudo num banco de dados do Notion.

## Como funciona

1. Compartilhe um vídeo/imagem/link de receita pro app (ou descreva o prato em
   texto direto na tela inicial).
2. O app comprime o vídeo, sobe pro Gemini e pede pra IA extrair a receita
   estruturada (título, ingredientes, modo de preparo, tags, dicas).
3. Você revisa e salva no seu banco de dados do Notion.
4. A partir daí, dá pra buscar, filtrar por tag/status/favorito, editar e
   apagar as receitas salvas — tudo sincronizado com o Notion.

## Setup

### 1. Pré-requisitos

- Android Studio (Giraffe ou mais recente)
- JDK 17
- `compileSdk`/`targetSdk` 34, `minSdk` 33 (já configurados no projeto)

### 2. Segredos (`local.properties`)

O app precisa de chaves de API do Gemini e de um token de integração do
Notion. **Nenhum segredo fica no código-fonte** — tudo vem de
`local.properties`, na raiz do projeto (esse arquivo nunca é versionado, veja
`.gitignore`). Crie/edite o seu com:

```properties
sdk.dir=/caminho/pro/seu/Android/Sdk

GEMINI_API_KEY_NATHAFF=sua_chave_aqui
GEMINI_API_KEY_NATHHIA=sua_chave_aqui
GEMINI_API_KEY_ANDERSON=sua_chave_aqui
GEMINI_API_KEY_FELIPE=sua_chave_aqui

NOTION_TOKEN=seu_token_de_integracao_do_notion
NOTION_DATABASE_ID=id_do_seu_banco_de_dados_no_notion
```

- As 4 chaves do Gemini existem porque o app faz **rotação automática de
  chave** quando uma bate cota (erro 429) — veja `ApiKeyManager`. Se você não
  tiver 4 chaves, pode repetir a mesma chave nas 4 variáveis (a rotação só vai
  ser inútil, mas o app funciona normalmente).
- O `NOTION_TOKEN` é o token de uma [integração interna do
  Notion](https://www.notion.so/my-integrations), com acesso concedido ao seu
  banco de dados de receitas.
- O `NOTION_DATABASE_ID` é o ID do banco (está na URL do banco no Notion).

Essas variáveis são lidas em `app/build.gradle.kts` e expostas ao código via
`BuildConfig`.

#### Colunas esperadas no banco do Notion

O banco de dados do Notion precisa ter estas colunas (nomes exatos, incluindo
maiúsculas): `Nome` (título), `Tempo de Preparo` (texto), `Ingredientes`
(texto), `Preparo` (texto), `Tags` (multi-select), `Favorito` (checkbox),
`Status` (status, com opções `Feito`/`Não feito`/`Quero fazer`), `Link`
(URL), `Dicas` (texto).

### 3. Rodar o app

```sh
./gradlew installDebug
```

Ou abra o projeto no Android Studio e rode normalmente (▶️).

### 4. Rodar os testes

```sh
./gradlew testDebugUnitTest
```

## Arquitetura

Código Kotlin em `app/src/main/java/com/bakeitoff/`, organizado por camada:

```
com.bakeitoff/
├── MainActivity.kt       — entry point, trata intents de compartilhamento
├── AppNavigation.kt      — grafo de navegação (Compose Navigation)
├── data/
│   ├── model/            — Receita, Ingrediente, DicasComentario
│   ├── notion/           — NotionRepository, API do Notion (Retrofit)
│   └── gemini/           — extração via IA: GeminiFileUploader,
│                           RecipeExtractor, RecipeExtractionRepository,
│                           MediaPreparer (compressão de vídeo/imagem),
│                           ApiKeyManager (rotação de chave)
├── viewmodel/            — RecipeViewModel, RecipeJsonParser
└── ui/
    ├── screens/          — telas Compose
    └── theme/            — Material 3 theme
```

`RecipeViewModel` delega a lógica pesada pras classes de `data.gemini`
(preparo de mídia, pipeline de extração) e usa `RecipeJsonParser` pra
converter a resposta em JSON da IA num objeto `Receita` de forma segura —
inclusive contra o comportamento do Gson de preencher campos "não-nulos" do
Kotlin com `null` quando a IA esquece uma chave no JSON (veja os comentários
em `RecipeJsonParser.sanitize`).

## Contribuindo

Esse é um projeto pessoal/entre amigos (Nathhia, Nathaff, Anderson, Felipe).
Pull requests são bem-vindos — só lembre de nunca commitar valores reais de
`local.properties` nem tokens/chaves em código.
