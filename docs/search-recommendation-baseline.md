# Busca e recomendação: consultas de referência, métricas e baseline

Parte do epic [#226](https://github.com/gustavomachadosilva/amazon-clone/issues/226) (revisão dos
algoritmos de busca e recomendação). Este documento é o entregável do card
[#219](https://github.com/gustavomachadosilva/amazon-clone/issues/219). Ele registra **como** a busca
é medida e **quanto** o algoritmo atual marca, para que cada card seguinte do epic (#222, #220, #221
na busca; #223, #224, #225 na recomendação) compare seus números com os daqui em vez de avaliar
no olho.

## O que existe

| Artefato | Caminho |
|---|---|
| Consultas de referência (30) | `backend/src/test/resources/search-eval/queries.json` |
| Dataset de recomendação (pedidos, reviews, listas fictícios) | `backend/src/test/resources/search-eval/recommendation-dataset.json` |
| Métricas (funções puras) + testes | `backend/src/test/java/com/mercatto/integration/SearchMetrics.java`, `SearchMetricsTest.java` |
| Validação das fixtures (sempre roda, sem Docker) | `.../integration/SearchEvalFixturesTest.java` |
| Carregador do dataset (só via API HTTP pública) | `.../integration/RecommendationDatasetLoader.java` |
| Harness que roda as consultas e imprime/grava as métricas | `.../integration/SearchEvalIT.java` |

## Algoritmo avaliado (baseline)

`GET /api/catalog/products?query=…&page=0&size=10` → `ProductController.search` →
`ProductServiceImpl.searchWithRating` → `ProductRepository.search`:

```sql
select p from Product p
where (:query is null or lower(p.name) like lower(concat('%', :query, '%')))
  and (:category is null or p.category = :category)
```

- Substring, sem diferenciar maiúsculas, **só no nome** (nem `brand`, nem `category`, nem
  `description`).
- A consulta inteira é uma única substring: sem separar termos, sem plural/singular, sem tolerância
  a erro de digitação.
- `%` e `_` da consulta **não são escapados** e viram curingas do `LIKE`.
- **Sem `ORDER BY`**: a ordem é a física da tabela no PostgreSQL (na prática, a ordem de inserção
  do seed, mas uma linha atualizada, por exemplo pela baixa de estoque de um pedido, pode mudar de
  posição).

## Metodologia

### Consultas de referência

30 consultas sobre o seed de 500 produtos (`amazon-products-sample.csv`: 25 categorias × 20
produtos, nomes únicos), cobrindo 7 tipos:

| Tipo | n | Consultas |
|---|---|---|
| `exact` | 6 | playstation, lipstick, serum, handbag, camera, samsung |
| `multi_term` | 6 | wireless earbuds, earbuds wireless, running shoes, usb c cable, noise cancelling headphones, nintendo switch |
| `plural_singular` | 4 | boots, boot, laptops, headphone |
| `typo` | 3 | headphnes, playstaton, lipstik |
| `category_only` | 4 | video games, home appliances, computer components, furniture |
| `special_chars` | 4 | `100%`, `%`, `_`, `50% off` |
| `no_result` | 3 | xyzzy, blender, air fryer |

**Julgamento de relevância por intenção, não por substring.** Quem busca "camera" quer uma câmera,
não um filme Polaroid nem um tablet "com câmera de 5MP"; quem busca "samsung" quer produtos
Samsung, não um controle remoto "para TV Samsung"; "lipstick" inclui o lip tint que não tem a
palavra no nome. Cada consulta traz em `notes` o critério usado. O conjunto relevante de uma
consulta é a união de `relevant` (nomes exatos de produtos do seed) com todos os produtos de
`relevantCategories`. Consultas com conjunto vazio (`_`, `50% off` e as `no_result`) medem falso
positivo: o certo é não devolver nada.

`SearchEvalFixturesTest` (roda em todo `mvn test`, sem Docker) garante que todo nome e categoria
citados existem no seed, que os ids são únicos, que há 20–30 consultas cobrindo todos os tipos e que
as `no_result` de fato não aparecem em nenhum nome ou categoria.

### Métricas

k = 10 (a página que o frontend pede: `page=0&size=10`). Implementação em `SearchMetrics`.

| Métrica | Definição |
|---|---|
| **P@10** | relevantes entre os resultados mostrados na 1ª página ÷ nº de resultados mostrados (`min(10, total)`); 0 se nada volta. Dividir pelo que é mostrado (e não por 10) evita que uma consulta com 1–3 relevantes fique limitada a 0,1–0,3 mesmo quando responde perfeitamente. |
| **R@10** | relevantes na 1ª página ÷ `min(10, nº de relevantes)`: vale 1 quando a primeira página está tão cheia de relevantes quanto possível. |
| **MRR@10** | média de `1 / posição do 1º relevante` na 1ª página (0 se nenhum). |
| **Taxa de zero-resultado** | fração das consultas *com relevantes* que voltam `totalElements = 0`. |
| **Taxa de falso positivo** | fração das consultas *sem relevantes* que devolvem algo. |
| **Latência p50/p95/max** | tempo da chamada HTTP `GET /api/catalog/products` medido pelo cliente, percentil pelo método nearest-rank. |

P@10, R@10 e MRR são médias sobre as 25 consultas com relevantes; as 5 sem relevantes entram só no
falso positivo.

### Latência

- A aplicação inteira sobe (`@SpringBootTest`, porta aleatória, perfil `dev`) contra PostgreSQL 16
  em Testcontainers; o cliente (`TestRestTemplate`) roda no mesmo processo.
- A rodada de relevância serve de aquecimento e é descartada. Depois, 20 rodadas
  (`-Dsearch.eval.repetitions=N` muda) de todas as 30 consultas, cronometrando com `System.nanoTime()`
  em volta da chamada: 600 amostras no total, 20 por consulta.
- Máquina do baseline: MacBook Apple Silicon (aarch64, 10 CPUs), macOS, Java 21.0.1, Docker via
  Colima. Números de latência só são comparáveis na mesma máquina; ao comparar, rode o baseline e a
  mudança lado a lado.

### Como reproduzir

```bash
cd backend
# Com Colima (ver README > Testes de integração):
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
       TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock TESTCONTAINERS_RYUK_DISABLED=true
mvn test -Dtest=SearchEvalIT
```

O teste imprime a tabela no console e grava `backend/target/search-eval/report.md` e
`report.json` (este último inclui, por consulta, a primeira página devolvida com cada item marcado
como relevante ou não). O sufixo `IT` o deixa fora do `mvn test` normal; ele precisa rodar
**sozinho**, porque exige o catálogo com exatamente os 500 produtos do seed (os outros testes de
integração criam produtos no mesmo container) e falha com essa instrução se não for o caso.

Antes das consultas, o teste carrega o dataset de recomendação pelas APIs públicas (ver abaixo),
para que o ambiente seja o mesmo que os cards de recomendação vão usar e para que uma ordenação que
use nota/vendas (#221) tenha dados. As métricas de relevância saíram idênticas nas 3 execuções.

## Resultados do baseline

Medido em 26/09/2026 sobre o `dev` do commit que introduziu este documento. Relevância idêntica
nas 3 execuções; latência = mediana das 3 execuções.

### Agregado

| Métrica | Baseline |
|---|---|
| P@10 | **0,498** |
| R@10 | **0,482** |
| MRR@10 | **0,524** |
| Taxa de zero-resultado (consultas com relevantes) | **0,32** (8 de 25) |
| Taxa de falso positivo (consultas sem relevantes) | **0,20** (1 de 5: `_`) |
| Latência p50 | 4,48 ms |
| Latência p95 | **7,54 ms** (execuções: 7,54 / 7,40 / 7,63) |
| Latência max | 14,64 ms |

### Por tipo

| Tipo | n | P@10 | R@10 | MRR@10 | zero-resultado | falso positivo | p95 ms |
|---|---|---|---|---|---|---|---|
| exact | 6 | 0,629 | 0,756 | 0,694 | 0,000 | – | 8,03 |
| multi_term | 6 | 0,778 | 0,655 | 0,722 | 0,167 | – | 7,19 |
| plural_singular | 4 | 0,683 | 0,600 | 0,750 | 0,250 | – | 8,00 |
| typo | 3 | 0,000 | 0,000 | 0,000 | 1,000 | – | 4,15 |
| category_only | 4 | 0,167 | 0,050 | 0,250 | 0,750 | – | 5,49 |
| special_chars | 4 | 0,300 | 0,500 | 0,306 | 0,000 | 0,500 | 7,42 |
| no_result | 3 | – | – | – | – | 0,000 | 4,11 |

### Por consulta

`relevantes` = tamanho do conjunto relevante; `total` = `totalElements` devolvido; `hits` =
relevantes na 1ª página.

| id | tipo | consulta | relevantes | total | hits | P@10 | R@10 | RR | p95 ms |
|---|---|---|---|---|---|---|---|---|---|
| exact-playstation | exact | `playstation` | 5 | 4 | 3 | 0,750 | 0,600 | 1,000 | 6,30 |
| exact-lipstick | exact | `lipstick` | 3 | 2 | 1 | 0,500 | 0,333 | 0,500 | 5,97 |
| exact-serum | exact | `serum` | 3 | 5 | 3 | 0,600 | 1,000 | 1,000 | 6,23 |
| exact-handbag | exact | `handbag` | 17 | 7 | 6 | 0,857 | 0,600 | 1,000 | 5,95 |
| exact-camera | exact | `camera` | 4 | 12 | 4 | 0,400 | 1,000 | 0,333 | 8,81 |
| exact-samsung | exact | `samsung` | 6 | 9 | 6 | 0,667 | 1,000 | 0,333 | 6,22 |
| multi-wireless-earbuds | multi_term | `wireless earbuds` | 17 | 14 | 10 | 1,000 | 1,000 | 1,000 | 8,32 |
| multi-earbuds-wireless | multi_term | `earbuds wireless` | 17 | 0 | 0 | 0,000 | 0,000 | 0,000 | 4,52 |
| multi-running-shoes | multi_term | `running shoes` | 6 | 3 | 3 | 1,000 | 0,500 | 1,000 | 5,76 |
| multi-usb-c-cable | multi_term | `usb c cable` | 1 | 1 | 1 | 1,000 | 1,000 | 1,000 | 5,88 |
| multi-noise-cancelling-headphones | multi_term | `noise cancelling headphones` | 7 | 3 | 3 | 1,000 | 0,429 | 1,000 | 5,93 |
| multi-nintendo-switch | multi_term | `nintendo switch` | 4 | 6 | 4 | 0,667 | 1,000 | 0,333 | 6,06 |
| plural-boots | plural_singular | `boots` | 10 | 6 | 5 | 0,833 | 0,500 | 1,000 | 5,73 |
| plural-boot | plural_singular | `boot` | 10 | 11 | 9 | 0,900 | 0,900 | 1,000 | 7,65 |
| plural-laptops | plural_singular | `laptops` | 7 | 0 | 0 | 0,000 | 0,000 | 0,000 | 4,35 |
| plural-headphone | plural_singular | `headphone` | 30 | 20 | 10 | 1,000 | 1,000 | 1,000 | 8,25 |
| typo-headphnes | typo | `headphnes` | 30 | 0 | 0 | 0,000 | 0,000 | 0,000 | 4,21 |
| typo-playstaton | typo | `playstaton` | 5 | 0 | 0 | 0,000 | 0,000 | 0,000 | 3,95 |
| typo-lipstik | typo | `lipstik` | 3 | 0 | 0 | 0,000 | 0,000 | 0,000 | 4,15 |
| category-video-games | category_only | `video games` | 20 | 0 | 0 | 0,000 | 0,000 | 0,000 | 3,99 |
| category-home-appliances | category_only | `home appliances` | 20 | 0 | 0 | 0,000 | 0,000 | 0,000 | 4,29 |
| category-computer-components | category_only | `computer components` | 20 | 0 | 0 | 0,000 | 0,000 | 0,000 | 4,21 |
| category-furniture | category_only | `furniture` | 20 | 3 | 2 | 0,667 | 0,200 | 1,000 | 5,64 |
| special-100-percent | special_chars | `100%` | 6 | 21 | 5 | 0,500 | 0,833 | 0,500 | 7,47 |
| special-percent | special_chars | `%` | 6 | 500 | 1 | 0,100 | 0,167 | 0,111 | 6,05 |
| special-underscore | special_chars | `_` | 0 | 500 | 0 | – | – | – | 7,18 |
| special-50-percent-off | special_chars | `50% off` | 0 | 0 | 0 | – | – | – | 4,02 |
| none-xyzzy | no_result | `xyzzy` | 0 | 0 | 0 | – | – | – | 4,09 |
| none-blender | no_result | `blender` | 0 | 0 | 0 | – | – | – | 4,15 |
| none-air-fryer | no_result | `air fryer` | 0 | 0 | 0 | – | – | – | 4,26 |

## Problemas conhecidos que o baseline evidencia

- **`%` e `_` não escapados.** `%` e `_` devolvem os 500 produtos (`_` é o único falso positivo do
  conjunto); `100%` vira "contém 100" e traz 21 resultados (livro "100 Words", "100PCS"…) em vez dos 6
  com "100%" literal. `50% off` só não dá falso positivo por acaso: nenhum nome tem "50" seguido de
  " off".
- **Termos na ordem exata.** `wireless earbuds` acerta 10/10, mas `earbuds wireless` (mesma
  intenção) volta vazio. `noise cancelling headphones` só acha os 3 nomes com a frase contígua de 7
  relevantes.
- **Sem plural/singular.** `laptops` volta vazio (nenhum nome tem o plural), enquanto `laptop` teria
  resultado; `boots` devolve 6 resultados (5 botas) e `boot`, 11 (as 10 botas).
- **Sem tolerância a erro de digitação.** As 3 consultas `typo` voltam vazias.
- **Categoria não é pesquisada.** `video games`, `home appliances` e `computer components` voltam
  vazias, apesar de 20 produtos cada; `furniture` só acha os 2 nomes que citam a palavra (e uma capa de
  sofá de Home Decor).
- **Ordem sem significado.** Sem `ORDER BY`, o resultado segue a ordem física da tabela: acessórios
  e produtos "compatíveis com" aparecem antes do produto buscado (`camera` e `samsung`: 1º relevante
  na posição 3; `nintendo switch`: na 3). Atualizar uma linha pode mudar sua posição: no teste, o
  PS5, que teve estoque baixado pelos pedidos do dataset, aparece por último em `playstation`, depois
  do cabo de força. Sem ordem definida, a paginação também não é estável (#221).
- **`description` e `brand` pobres no seed.** O CSV só traz `name, price, stock_quantity, category,
  image_url`: `description` fica vazia e `brand` é a primeira palavra do nome (heurística do
  `AmazonProductSeeder`, que erra em nomes genéricos). Buscar nesses campos (#220) terá pouco ganho
  medido neste seed; o ganho medido virá de categoria, múltiplos termos e normalização.
- **Não há nada no seed para uma parte das intenções** (`blender`, `air fryer`): acertam hoje (0
  resultados) e devem continuar acertando: uma busca mais "frouxa" não pode passar a devolver lixo
  para elas nem para `xyzzy`.

## Dataset de recomendação

`recommendation-dataset.json` é uma **fixture de teste de integração**, nunca entra no seed de
produção. Existe porque o seed não tem pedidos, reviews nem listas, então nenhum algoritmo de
recomendação poderia ser avaliado localmente.

- **10 compradores simbólicos** em três grupos de gosto mais um de ruído: `gamer-1..3` (Video Games,
  Headphones & Earbuds, Computer Components), `beauty-1..3` (Makeup, Skin Care Products),
  `home-1..3` (Bedding, Home Decor Products, Kitchen & Dining) e `mixed-1`, que mistura grupos
  (PS5 + controle, velas + lip oil, Switch + espátulas).
- **24 pedidos** (quantidade 1; a demanda total de cada produto cabe no estoque do seed), com
  co-compras plantadas: PS5 + DualSense (3×), PS5 + Spider-Man 2 (2×), RTX 4060 + Ryzen 5 + DDR5
  (2×), corretivo + base (3×), sérum + hidratante (2×), travesseiros + protetor de colchão (3×),
  chaleira + chá (2×).
- **37 reviews** (1–5 estrelas, maioria alta dentro do próprio grupo, algumas notas baixas) e
  **10 listas** com 22 itens.
- **Expectativas** para os cards de recomendação:
  - `alsoBought`: para 6 produtos, o que um "frequently bought together" (#224) deve trazer.
  - `forBuyer`: para `gamer-3`, `beauty-2` e `home-2`, produtos que o comprador nunca comprou, avaliou
    nem pôs em lista, mas que os outros do mesmo grupo compraram. Um "Recommended for you" (#225)
    deveria trazê-los.

`SearchEvalFixturesTest` verifica que as co-compras esperadas aparecem juntas em ≥ 2 pedidos e que os
itens `heldOut` nunca foram tocados pelo comprador.

**Como os cards #223–#225 usam o dataset:** num teste de integração com `@ActiveProfiles("dev")`
(como o `SearchEvalIT`), montar o mapa nome → id paginando `GET /api/catalog/products` e chamar
`new RecommendationDatasetLoader(this).load(SearchEvalFixtures.dataset(), idsByName)`. O loader
registra os compradores e cria pedidos (`POST /api/orders/checkout`), reviews
(`POST /api/reviews/products/{id}`) e listas (`POST /api/lists`, `POST /api/lists/{id}/items`) só pela
API HTTP pública, sem repositório nem SQL em schema de outro módulo (Contrato de Modularidade). O
resultado (`LoadedDataset`) traz comprador simbólico → usuário/token e nome → id do produto, para
consultar o endpoint de recomendação e comparar com `expectations`.

**Ainda não existe baseline de recomendação.** Hoje a página de produto mostra os 10 primeiros itens
da mesma categoria e a Home, os 10 primeiros do banco para todos: não há endpoint próprio de
recomendação a medir. As métricas de recomendação (por exemplo, hit rate@k de `alsoBought` e de
`forBuyer`) devem ser definidas pelo primeiro card que criar esse endpoint (#223), registrando aqui o
número do placeholder atual e o da mudança.

## Como atualizar este documento

- Cada card do epic roda `mvn test -Dtest=SearchEvalIT` antes e depois da mudança, na mesma máquina,
  e **acrescenta** uma coluna às tabelas de agregado e por tipo (ex.: "após #220"). A coluna
  "Baseline" nunca é sobrescrita.
- Se um card precisar mudar as consultas ou o julgamento, suba `version` em `queries.json`, explique
  o motivo e rode o baseline de novo com a nova versão, para comparar sempre a mesma régua.
- Consultas novas entram como linhas novas (com `notes` explicando o julgamento); não reaproveite `id`.
