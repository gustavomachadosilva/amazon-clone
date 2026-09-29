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
# O Maven precisa rodar num JDK 21 (num JDK mais novo o Lombok não processa as anotações):
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
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

| Métrica | Baseline | Após #222 | Após #220 | Após #221 |
|---|---|---|---|---|
| P@10 | **0,498** | 0,498 | **0,794** | **0,810** |
| R@10 | **0,482** | 0,482 | **0,859** | **0,880** |
| MRR@10 | **0,524** | 0,551 | **0,883** | **1,000** |
| Taxa de zero-resultado (consultas com relevantes) | **0,32** (8 de 25) | 0,32 (8 de 25) | **0,00** (0 de 25) | **0,00** (0 de 25) |
| Taxa de falso positivo (consultas sem relevantes) | **0,20** (1 de 5: `_`) | 0,20 (1 de 5: `_`) | **0,00** (0 de 5) | **0,00** (0 de 5) |
| Latência p50 | 4,48 ms | 4,39 ms | 2,25 ms | 2,93 ms (#220 remedido: 2,94 ms) |
| Latência p95 | **7,54 ms** (execuções: 7,54 / 7,40 / 7,63) | 7,48 ms (1 execução) | **9,85 ms** (execuções: 10,23 / 9,85 / 9,70) | **10,44 ms** (execuções: 10,24 / 10,88 / 10,44; #220 remedido: 11,18 ms) |
| Latência max | 14,64 ms | 20,96 ms | 14,26 ms | 16,71 ms (#220 remedido: 21,56 ms) |

"Após #222" e "Após #220" foram medidos em 29/09/2026 na mesma máquina do baseline: #222 no `dev`
antes de #220 (commit `820d447`), #220 no branch do card. Relevância idêntica nas execuções.
#222 só mudou o MRR: a ordem deixou de ser a física da tabela e passou a ser `id ASC`, o que tirou
`nintendo switch` da posição 3 (RR 0,333 → 1). Ver [Card #220](#card-220-busca-em-múltiplos-campos-e-termos)
para o que mudou e por que a latência p95 subiu.

"Após #221" foi medido em 29/09/2026 na mesma máquina, no branch do card, lado a lado com o `dev`
de #220 (commit `165392b`) remedido na mesma sessão: relevância do #220 idêntica à da coluna "Após
#220" (MRR 0,883), latência um pouco mais alta que a registrada antes (máquina com outra carga), por
isso a latência do #221 deve ser comparada com o "#220 remedido" entre parênteses. Relevância
idêntica nas 3 execuções de cada lado. Ver [Card #221](#card-221-ordenação-real-por-relevância).

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

Após #222 e após #220, por tipo (célula = `após #222 → após #220`; p95 da execução mediana):

| Tipo | n | P@10 | R@10 | MRR@10 | zero-resultado | falso positivo | p95 ms |
|---|---|---|---|---|---|---|---|
| exact | 6 | 0,629 → 0,636 | 0,756 → 0,806 | 0,694 → 0,722 | 0,000 → 0,000 | – | 7,63 → 3,25 |
| multi_term | 6 | 0,778 → 0,813 | 0,655 → 0,952 | 0,833 → 1,000 | 0,167 → 0,000 | – | 7,97 → 3,13 |
| plural_singular | 4 | 0,683 → 0,750 | 0,600 → 0,771 | 0,750 → 0,813 | 0,250 → 0,000 | – | 7,61 → 3,29 |
| typo | 3 | 0,000 → 0,750 | 0,000 → 0,644 | 0,000 → 0,833 | 1,000 → 0,000 | – | 5,06 → 12,92 |
| category_only | 4 | 0,167 → 0,975 | 0,050 → 0,975 | 0,250 → 1,000 | 0,750 → 0,000 | – | 5,29 → 3,34 |
| special_chars | 4 | 0,300 → 1,000 | 0,500 → 1,000 | 0,306 → 1,000 | 0,000 → 0,000 | 0,500 → 0,000 | 8,03 → 2,83 |
| no_result | 3 | – | – | – | – | 0,000 → 0,000 | 4,89 → 10,18 |

Após #221, por tipo (célula = `após #220 remedido → após #221`, medidos lado a lado na mesma sessão;
p95 da execução mediana de cada lado):

| Tipo | n | P@10 | R@10 | MRR@10 | zero-resultado | falso positivo | p95 ms |
|---|---|---|---|---|---|---|---|
| exact | 6 | 0,636 → 0,636 | 0,806 → 0,806 | 0,722 → **1,000** | 0,000 → 0,000 | – | 4,53 → 4,23 |
| multi_term | 6 | 0,813 → 0,797 | 0,952 → 0,936 | 1,000 → 1,000 | 0,000 → 0,000 | – | 4,46 → 4,34 |
| plural_singular | 4 | 0,750 → **0,875** | 0,771 → **0,929** | 0,813 → **1,000** | 0,000 → 0,000 | – | 4,65 → 4,35 |
| typo | 3 | 0,750 → 0,750 | 0,644 → 0,644 | 0,833 → **1,000** | 0,000 → 0,000 | – | 15,13 → 14,92 |
| category_only | 4 | 0,975 → 0,975 | 0,975 → 0,975 | 1,000 → 1,000 | 0,000 → 0,000 | – | 5,06 → 4,89 |
| special_chars | 4 | 1,000 → 1,000 | 1,000 → 1,000 | 1,000 → 1,000 | 0,000 → 0,000 | 0,000 → 0,000 | 4,16 → 3,23 |
| no_result | 3 | – | – | – | – | 0,000 → 0,000 | 11,25 → 9,88 |

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

## Card #220: busca em múltiplos campos e termos

[#220](https://github.com/gustavomachadosilva/amazon-clone/issues/220) substituiu o
`lower(name) like '%query%'` por full-text search do PostgreSQL. Objetos SQL em
`backend/src/main/resources/db/post-ddl/catalog-search.sql` (idempotente, roda a cada subida logo
depois do `ddl-auto`, via `spring.sql.init` + `spring.jpa.defer-datasource-initialization`); parse
da consulta em `catalog.repository.ProductTextQuery`; predicados em `ProductSpecifications`
(`matchesText`, `orderByRelevance`); correção de digitação em
`ProductRepository#findClosestIndexedWord` e `ProductServiceImpl#searchWithRating`.

### Algoritmo

1. **Parse** (`ProductTextQuery`): trim, no máximo 200 caracteres e 10 termos, separação por espaço.
   Um token com `%` ou `_` vira **termo literal**: padrão `LIKE '%…%'` com `\`, `%` e `_` escapados.
   Os demais tokens são quebrados em tudo que não é letra/dígito (`usb-c` → `usb`, `c`), em
   minúsculas: **termos full-text**. Uma consulta só de pontuação (`!!!`) vira um termo literal.
2. **Casamento** (`catalog.product_fts_matches`): documento
   `setweight(name,'A') || setweight(brand,'B') || setweight(category,'B') || setweight(description,'C')`,
   cada campo passando por `unaccent` e `to_tsvector('english', …)`; consulta
   `to_tsquery('english', 't1:* & t2 & …')`: **todos** os termos precisam casar (em qualquer campo e
   ordem), com stemming do inglês (`laptops` ↔ `laptop`, `running` ↔ `run`) e prefixo (`:*`) para
   termos de 3+ caracteres. Como os termos só têm letras e dígitos, nenhum operador de tsquery
   (`& | ! ( ) : *`) vindo do usuário chega ao `to_tsquery`. Termos literais precisam ser
   substring de `catalog.product_search_text` (os 4 campos concatenados, sem acento, minúsculos).
3. **Ordenação** (`sort=relevance`, o padrão, com termos full-text): `ts_rank` sobre o mesmo
   documento, pesos padrão do PostgreSQL (A = 1,0, B = 0,4, C = 0,2; nenhum peso foi ajustado), depois
   `id ASC`. Sem consulta, ou só com termos literais, continua `id ASC`. Os outros `sort` não mudaram.
4. **Tolerância a erro de digitação**, só quando a busca volta `totalElements = 0` e há termos
   full-text: cada termo só de letras com 4+ caracteres é trocado pela palavra do catálogo mais
   próxima (`ts_stat` sobre `to_tsvector('simple', product_search_text)`) com distância de
   Levenshtein ≤ 1 (≤ 2 a partir de 8 letras) e similaridade de trigramas ≥ 0,45; desempate por
   menor distância, maior similaridade e presença em mais produtos. Se algum termo mudou, a busca
   roda **uma** vez de novo com os mesmos filtros; senão o resultado vazio é devolvido. Termos
   com dígito (`ps5`, `4060`) nunca são corrigidos.
5. **Índices**: GIN sobre `catalog.product_search_vector(...)` (usado pelo casamento full-text) e
   GIN trigram sobre `catalog.product_search_text(...)` (disponível para os termos literais). As
   funções são `IMMUTABLE` (o `unaccent` é embrulhado com dicionário fixo em
   `catalog.immutable_unaccent`) e SQL simples, então o planner as expande e casa com os índices.

Limitações conhecidas: uma consulta só de stop words do inglês (`the`, `for`) não filtra nada e
devolve o catálogo inteiro (a alternativa, não devolver nada, é pior); a correção de digitação
varre o vocabulário inteiro (`ts_stat`), por isso só roda quando não houve resultado.

### Latência

O p50 caiu pela metade (4,4 → 2,3 ms): com o índice GIN, o casamento não varre a tabela. O p95
subiu (7,5 → 9,9 ms) porque 6 das 30 consultas (3 `typo` e 3 `no_result`) voltam vazias na
primeira tentativa e pagam o `ts_stat` da correção (≈ 7–8 ms a mais); com 20% das consultas nesse
caminho, o p95 cai nele. As demais consultas ficaram em ~2–3,5 ms de p95.

Uma medição intermediária mostrou p95 de 26 ms: logo depois do seed a tabela ainda não tem
estatísticas (o autovacuum só roda `ANALYZE` depois de ~1 min), o planner acha que ela está vazia e
escolhe seq scan, recalculando o `to_tsvector` dos 500 produtos a cada busca. Por isso o
`AmazonProductSeeder` agora roda `ANALYZE catalog.products` logo depois da carga
(`ProductRepository#refreshStatistics`); com estatísticas, `EXPLAIN ANALYZE` mostra Bitmap Index
Scan em `products_search_vector_idx` (≈ 0,4 ms contra ≈ 10 ms do seq scan).

### O que mudou por consulta (após #222 → após #220)

- **Resolvidas (0 → resultado):** `earbuds wireless` (0 → 22, P@10 1,0), `laptops` (0 → 15),
  as 3 `typo` (`headphnes` → headphones, `playstaton` → playstation, `lipstik` → lipstick: mesmos
  resultados das consultas corretas), e `video games`, `home appliances`, `computer components` (0 →
  20, P@10 1,0, pela categoria).
- **Mais recall:** `furniture` (3 → 21, R@10 0,2 → 0,9), `handbag` (7 → 20, R@10 0,6 → 0,9),
  `running shoes` (R@10 0,5 → 1,0), `noise cancelling headphones` (R@10 0,43 → 0,71), `boots` (R@10
  0,5 → 0,9, agora igual a `boot`).
- **`%` e `_` literais:** `100%` (21 → 6, P@10 0,5 → 1,0), `%` (500 → 6, só os produtos com `%`),
  `_` (500 → 0: some o único falso positivo), `50% off` segue 0.
- **Continuam vazias, como devem:** `xyzzy`, `blender`, `air fryer` (nenhuma palavra do catálogo a
  ≤ 1–2 edições).
- **Pioras / pontos fracos restantes** (ranking, escopo de #221):
  - `laptops`: acha os 7 notebooks, mas só 2 na 1ª página (P@10 0,2): fones "…for Laptop" têm o
    termo no nome com o mesmo peso A e empatam com os notebooks; o desempate é o `id`.
  - `playstation`: RR 1,0 → 0,5: o cabo de força cita "Playstation" 3 vezes no nome e o `ts_rank`
    premia a frequência.
  - `usb c cable` (P@10 1,0 → 0,5) e `noise cancelling headphones` (P@10 1,0 → 0,71): exigir os
    termos em qualquer posição (e não mais como frase contígua) traz itens que os citam
    separadamente: o carregador "USB C GaN Charger … (Cable Not Included)" e fones com "Noise
    Canceling Mic" (o stemming junta canceling/cancelling), que não são fones com cancelamento de
    ruído.
  - `samsung` e `camera` seguem com acessórios "for Samsung" / "for Canon … Camera" no topo
    (RR 0,333 e 0,5): o texto não distingue marca de compatibilidade.

## Card #221: ordenação real por relevância

[#221](https://github.com/gustavomachadosilva/amazon-clone/issues/221) trocou o `ts_rank` puro do
`sort=relevance` por uma pontuação em faixas. O **casamento não mudou** (mesmo
`catalog.product_fts_matches`, mesmo índice, mesmos resultados e `totalElements`): só a ordem.

### Fórmula

`catalog.product_relevance(name, brand, category, description, q_all, q_any, q_phrase)`, em
`db/post-ddl/catalog-search.sql`, usada só no `ORDER BY` (`ProductSpecifications.orderByRelevance`):

```
relevância =
    8 · [todos os termos na CABEÇA do nome]
  + 4 · [algum termo na marca]                       (q_any = termos unidos por |)
  + 2 · [todos os termos em qualquer lugar do nome]
  + 2 · [2+ termos como frase contígua no nome]      (q_phrase = termos unidos por <->)
  + 1 · [todos os termos na categoria]
  + ts_rank(documento, q_all, 1|32)                  (em [0, 1): só desempata dentro da faixa)
ORDER BY relevância DESC, id ASC
```

- Cada campo é casado com `to_tsvector('english', immutable_unaccent(campo)) @@
  to_tsquery('english', immutable_unaccent(q))`, a mesma normalização (stemming, sem acento,
  prefixo `:*` em termos de 3+ caracteres) do casamento de #220. `q_all`, `q_any` e `q_phrase` são
  montados por `ProductTextQuery` (`tsQuery()`, `anyTermTsQuery()`, `phraseTsQuery()`), inclusive na
  nova tentativa com a correção de digitação (`withReplacedTerms`).
- Os pesos seguem a ordem cabeça do nome > marca > resto do nome > categoria > descrição. A
  descrição não tem bônus próprio: só entra pelo `ts_rank` (peso C).
- `ts_rank` passou a usar normalização `1|32`: `1` divide por `1 + log(tamanho do documento)`, então
  um nome longo que repete a palavra não ganha mais por frequência; `32` leva o valor para
  `rank / (rank + 1)`, abaixo de 1, para que ele nunca pule de faixa.
- Sem termo full-text (sem consulta, ou só termos literais `%`/`_`) a ordem continua `id ASC`
  (`ProductSort.RELEVANCE.toSort()`); uma consulta só de stop words pontua 0 em todos os produtos e
  também cai em `id ASC`. Os outros `sort` não mudaram (todos terminam em `id ASC`).

### Cabeça do nome

`catalog.product_name_head(name)`: o nome (sem acento) até a primeira **palavra inteira**
`for | compatible | fits | replacement | replaces | para | compativel`, sem diferenciar maiúsculas; se
isso sobrar vazio (nome que começa com o marcador), o nome inteiro. É a heurística que separa o
produto do acessório feito para ele: "PlayStation 5 Console" tem "playstation" na cabeça; "AC Power
Cord Compatible Sony PS5 … Playstation 5 Playstation 4 …" só no rabo. Limitação: um nome que cita o
alvo antes do marcador ("Camera Backpack … for Laptop") continua na faixa de cima.

### Popularidade: decidido não usar

Nota média, nº de reviews ou vendas **não** entram na relevância: (1) o dataset de avaliação carrega
pedidos e reviews plantados para os cards de recomendação, então um sinal de popularidade mediria o
dataset e não a busca; (2) nota e estoque mudam a qualquer momento (uma review, um pedido), e uma
ordem que muda entre a página 1 e a página 2 repete ou pula produtos, justamente o problema do
card. Quem quer popularidade usa `sort=rating`. A ordem por relevância só depende dos campos de
texto e do `id`.

### Determinismo da paginação

`CatalogRelevanceIntegrationTest` cria 23 produtos numa categoria única, com faixas misturadas e
muitos empates exatos (nomes, marcas e preços idênticos). Para `relevance`, `price_asc`,
`price_desc` e `rating` com a consulta, e para `relevance`/padrão só com o filtro de categoria, lê
todas as páginas com `size=5` e verifica: nenhum id repetido, o conjunto é exatamente o criado, a
contagem é `totalElements` e a sequência é igual à de uma única página `size=100`. Uma variante
atualiza o estoque de dois produtos (um já visto, um ainda não) entre a página 0 e a 1 (o `UPDATE`
grava uma nova versão da linha, o que muda a ordem física da tabela) e exige o mesmo resultado.

### O que mudou por consulta (após #220 → após #221)

| Consulta | RR | P@10 | R@10 | O que aconteceu |
|---|---|---|---|---|
| `playstation` | 0,500 → **1,000** | 0,750 | 0,600 | PS5, DualSense e Mortal Kombat na frente; o cabo "…Compatible Sony PS5 … Playstation" (3× no rabo) foi para o fim. |
| `playstaton` (typo) | 0,500 → **1,000** | 0,750 | 0,600 | Mesma lista de `playstation`: a nova tentativa corrigida usa a mesma ordem. |
| `samsung` | 0,333 → **1,000** | 0,667 | 1,000 | Os 6 produtos SAMSUNG (cabeça + marca) nas posições 1–6; o controle "Universal for Samsung-TV-Remote" e a resistência "Heating Element for Samsung Dryer" em 7 e 8. |
| `camera` | 0,500 → **1,000** | 0,400 | 1,000 | Câmera ZWO em 1º; flash, filmes Polaroid e case "for … Camera" desceram para o fim da página. Continuam na página a mochila "Camera Backpack" (posição 2) e dois tablets "5MP Camera" (a palavra está na cabeça do nome). |
| `laptops` | 0,250 → **1,000** | 0,200 → **0,500** | 0,286 → **0,714** | Os 5 notebooks com "Laptop" no nome nas posições 1–5; fones "…for Laptop" e o drive "Compatible with Laptop" depois. Os 2 relevantes restantes (MacBook Pro, Surface Pro X) não têm a palavra e nunca casam. |
| `boots` / `boot` | 1,000 | 0,900 → **1,000** | 0,900 → **1,000** | A normalização por tamanho pôs a 10ª bota à frente do organizador de sapatos. |
| `usb c cable` | 1,000 | 0,500 | 1,000 | Sem mudança: só 2 resultados e o cabo já era o 1º. |
| `noise cancelling headphones` | 1,000 | 0,714 | 0,714 | Métricas iguais; os 3 com a frase contígua (2 Bose, JBL) nas posições 1–3 pelo bônus de frase, e o Galaxy Buds Live subiu de 6º para 5º. Os fones com "Noise Canceling Mic" continuam na página (têm os 3 termos na cabeça). |
| `earbuds wireless` | 1,000 | 1,000 → **0,900** | 1,000 → **0,900** | **Piora.** "Amazon Basics In Ear Wired Headphones, Earbuds with Microphone No Wireless Technology" entrou na página (posição 5): tem os dois termos na cabeça e, com a normalização por tamanho, o nome curto ganha dos fones sem fio de nome longo. Nenhum nome tem a frase na ordem invertida, então o bônus de frase não ajuda, e a negação ("No Wireless") está fora do alcance de uma ordenação lexical. `wireless earbuds` (ordem normal) continua com P@10 1,0. |

Nenhuma outra consulta mudou de P@10, R@10 ou RR; nenhum RR piorou.

### Latência

Medida lado a lado (3 execuções de cada lado, mesma sessão): p50 2,94 → 2,93 ms, p95 11,18 → 10,44 ms
(execuções do #221: 10,24 / 10,88 / 10,44), max 21,56 → 16,71 ms. Ou seja, sem custo mensurável: a
pontuação só é calculada para as linhas que já casaram (no máximo ~26 no seed), e o p95 continua
dominado pelas consultas que pagam a correção de digitação (`typo` e `no_result`, ~10–15 ms).

### Ressalva: sobreajuste

Os pesos e os marcadores da cabeça do nome foram escolhidos olhando para as mesmas 30 consultas que
medem o resultado, e o MRR 1,000 é sobre 25 consultas num seed de 500 produtos. Os sinais são
genéricos (nome > marca > categoria, "for/compatible/replacement" introduz compatibilidade, frase
contígua, frequência não conta) e nenhuma regra cita uma consulta, mas o número deve ser lido como
"os problemas de ordenação listados em #220 foram resolvidos", não como uma estimativa da qualidade
em consultas novas. Um conjunto de consultas separado (held-out) seria a medida honesta para
ajustes futuros de peso.

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

**Ainda não existe baseline numérico de recomendação.** Quando este documento foi escrito, a página
de produto mostrava os 10 primeiros itens da mesma categoria e a Home, os 10 primeiros do banco para
todos: não havia endpoint próprio de recomendação a medir. As métricas de recomendação (por exemplo,
hit rate@k de `alsoBought` e de `forBuyer`) devem ser definidas pelo primeiro card que criar esse
endpoint, registrando aqui o número do placeholder atual e o da mudança.

### Frequently bought together (#224)

- **Antes:** o bloco "Frequently bought together" da página de produto mostrava os 2 primeiros
  resultados de uma busca pela mesma categoria (`GET /api/catalog/products?category=…`), sem usar
  pedido nenhum. Não foi medido contra `alsoBought` — qualquer acerto seria coincidência da ordem da
  busca.
- **Depois:** `GET /api/orders/bought-together/{productId}?limit=2` conta co-compras em pedidos
  **PAID**, por **comprador distinto**, exige suporte mínimo de 2 compradores e ordena por
  `co / sqrt(popularidade)` (ver `orders.service.CoPurchaseScorer`). Sem co-compra suficiente, cai
  para os similares do #223 com `source: SIMILAR`, e a página não chama isso de "bought together".
- **Medido** em `FrequentlyBoughtTogetherIntegrationTest` (roda no `mvn test`): para os **6/6**
  produtos de `expectations.alsoBought`, com `limit=2`, a resposta é `CO_PURCHASE` e traz exatamente
  os itens esperados (hit rate@2 = 1,0 e nenhum item fora do esperado). No PS5, o DualSense (3
  compradores, score 1,73) vem antes do Spider-Man 2 (2 compradores, 1,41). A fonte "Corsair RM750e",
  comprada uma única vez junto com GPU/CPU/RAM (suporte 1), cai para `SIMILAR`, como esperado.
- Diferente do `SearchEvalIT`, esse teste não usa o perfil `dev`: cria pela API de vendedor um produto
  novo para cada nome do dataset e passa esse mapa nome → id ao `RecommendationDatasetLoader`, para que
  pedidos de outros testes não contaminem as contagens.

## Como atualizar este documento

- Cada card do epic roda `mvn test -Dtest=SearchEvalIT` antes e depois da mudança, na mesma máquina,
  e **acrescenta** uma coluna às tabelas de agregado e por tipo (ex.: "após #220"). A coluna
  "Baseline" nunca é sobrescrita.
- Se um card precisar mudar as consultas ou o julgamento, suba `version` em `queries.json`, explique
  o motivo e rode o baseline de novo com a nova versão, para comparar sempre a mesma régua.
- Consultas novas entram como linhas novas (com `notes` explicando o julgamento); não reaproveite `id`.
