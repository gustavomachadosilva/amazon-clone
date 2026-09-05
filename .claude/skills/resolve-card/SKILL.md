---
name: resolve-card
description: Look up a card (issue) on this repo's GitHub Project board by number, show its full details, and ask the user how to proceed. Use when the user runs /resolve-card <NUMBER> or asks to look up/start a project card or issue by number.
argument-hint: <NÚMERO DO CARD>
allowed-tools: [Bash(gh repo view*), Bash(gh project list*), Bash(gh project item-list*), Bash(gh issue view*), Bash(gh pr create*), Bash(git status*), Bash(git checkout*), Bash(git pull*), Bash(git push*), AskUserQuestion, Agent, Skill]
---

# /resolve-card — Buscar card no GitHub Projects

Busca um card pelo número no GitHub Project associado a este repositório, apresenta
suas informações ao usuário e pergunta como seguir. Não assuma uma ação (implementar,
mover status, etc.) sem o usuário confirmar.

## Argumento

Número do card: `$ARGUMENTS`

Se `$ARGUMENTS` estiver vazio ou não for um número, pergunte ao usuário qual o número
do card antes de continuar.

## Passos

1. **Descobrir owner/repo:**
   ```bash
   gh repo view --json owner,name --jq '.owner.login + "/" + .name'
   ```

2. **Descobrir o Project do repositório.** Liste os projects do owner e escolha o que
   corresponde a este repositório (título igual ou contendo o nome do repo,
   case-insensitive):
   ```bash
   gh project list --owner <owner> --format json
   ```
   Guarde o `number` do project encontrado. Se houver mais de um candidato plausível,
   pergunte ao usuário qual usar em vez de adivinhar.

3. **Buscar o item do card pelo número da issue** (o campo `content.number` no JSON do
   project é o número da issue/card):
   ```bash
   gh project item-list <PROJECT_NUMBER> --owner <owner> --format json --limit 200 \
     --jq '.items[] | select(.content.number == <NUMERO>)'
   ```

4. **Se não encontrar no project**, tente a issue diretamente no repositório (pode
   existir mas ainda não estar no board):
   ```bash
   gh issue view <NUMERO> --repo <owner>/<repo> --json number,title,state,body,labels,assignees,url
   ```
   Nesse caso, avise o usuário explicitamente que o card não está no board do project.

5. **Se não encontrar em nenhum dos dois**, informe claramente que o card/issue
   `<NUMERO>` não existe e pare — não invente dados.

## Apresentação

Ao encontrar o card, mostre de forma organizada (sem inventar campos que não vieram da
API):

- Título e número (com link da URL)
- Status, Priority, Size, Milestone (quando existirem no project)
- Labels
- Assignees
- Corpo/descrição da issue (geralmente contém contexto e "Critérios de aceite")
- Dependências, se mencionadas no corpo (ex.: "Depende de: ...")

## Depois de apresentar

Pergunte ao usuário como deseja seguir — não presuma a próxima ação. Ofereça
alternativas plausíveis (ex.: começar a implementar agora, criar uma branch, apenas
queria ver as informações, mover o status do card) mas deixe a decisão explicitamente
com o usuário antes de tomar qualquer ação no código ou no board.

Se o usuário confirmar que quer implementar o card agora, siga o fluxo orquestrado
abaixo.

## Execução orquestrada (planejamento → implementação)

Quando o usuário confirmar que quer implementar o card, não implemente você mesmo
diretamente nesta skill. Execute em duas etapas sequenciais, cada uma delegada a um
agente diferente via `Agent`. Cada agente é disparado sem memória desta conversa, então
todo prompt precisa ser autocontido: inclua número/título/URL do card, corpo completo e
critérios de aceite, labels e dependências, o trecho relevante do "Contrato de
Modularidade" (`README.md`) e o fluxo de Git do `CLAUDE.md`.

### Etapa 0 — Preparar a branch

Antes de acionar os agentes, siga o fluxo de Git do `CLAUDE.md`: atualize a `main`
local e crie a branch de trabalho a partir dela. Rode `git status` antes — se houver
mudanças não commitadas de outra tarefa, avise o usuário e não descarte nada.

```bash
git status
git checkout main
git pull
git checkout -b feature/<NUMERO>-<slug-curto-do-título>
```

O padrão de nome de branch já usado no repo é `feature/<numero>-<slug>` (ex.:
`feature/37-login-endpoint`) — minúsculo, palavras separadas por hífen.

### Etapa 1 — Planejamento (agente `Plan`)

Chame `Agent` com `subagent_type: "Plan"` e `run_in_background: false` (a etapa
seguinte depende do resultado). No prompt, dê ao agente:

- Todos os dados do card (título, corpo, critérios de aceite, labels, dependências,
  URL).
- O(s) módulo(s) de backend/frontend provavelmente afetado(s).
- As regras do "Contrato de Modularidade" (`README.md`) que se aplicam.
- Peça um plano concreto e passo a passo: arquivos a criar/alterar, ordem das
  mudanças, e estratégia de teste (ArchUnit/testes unitários no backend, lint/build no
  frontend) — só o plano, sem escrever código.

Quando o plano voltar, apresente ao usuário um resumo objetivo (passos principais,
arquivos envolvidos) e pergunte se pode seguir para a implementação com esse plano ou
se algo precisa ajustar antes. Não pule esta confirmação — é a única checagem antes de
código ser escrito.

### Etapa 2 — Implementação (agente diferente do planejamento)

Depois que o usuário aprovar o plano, chame `Agent` novamente com um `subagent_type`
diferente do usado na Etapa 1 (ex.: `general-purpose`), passando o plano aprovado e os
dados do card no prompt — de novo autocontido, este agente também não viu a conversa
nem o plano. Instrua-o a:

- Implementar seguindo o plano e o Contrato de Modularidade.
- Rodar `mvn test` (backend) e/ou `npm run lint && npm run build` (frontend), conforme
  o que foi alterado — o mesmo gate descrito no `CLAUDE.md`.
- Reportar o que foi feito, o que passou/falhou nos testes, e qualquer desvio do plano
  original com o motivo.

Rode esta etapa em foreground (`run_in_background: false`) quando o usuário estiver
esperando o resultado nesta conversa. Para cards grandes, pode oferecer rodar em
background e avisar o usuário quando terminar — mas confirme essa preferência com ele
antes, não decida sozinho.

### Depois da implementação

Resuma o que foi feito e pergunte ao usuário como quer seguir (`AskUserQuestion`),
oferecendo **abrir a PR direto** como opção padrão/recomendada — isso agora é o
comportamento normal desta skill, não é mais preciso confirmar cada vez com
antecedência. Mencione `/pr-check` (testes + revisão de código + checklist do Contrato
de Modularidade) como sugestão para quem quiser essa checagem extra antes, mas deixe
claro que não é obrigatório.

- **Se o usuário escolher abrir a PR direto**: push da branch criada na Etapa 0 e
  `gh pr create`:
  ```bash
  git push -u origin feature/<NUMERO>-<slug>
  gh pr create --base main --title "<título do card>" \
    --body "Closes #<NUMERO>

  <resumo curto do que foi implementado, com base no plano da Etapa 1>"
  ```
  Informe a URL da PR criada ao final.

- **Se o usuário escolher rodar o `/pr-check` antes**: chame
  `Skill(skill: "pr-check")` e, com o resultado em mãos, pergunte novamente como
  seguir (abrir a PR, corrigir algo primeiro, etc.).
