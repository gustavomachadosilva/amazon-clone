---
name: resolve-card
description: Look up a card (issue) on this repo's GitHub Project board by number, show its full details, and ask the user how to proceed. Use when the user runs /resolve-card <NUMBER> or asks to look up/start a project card or issue by number.
argument-hint: <NÚMERO DO CARD>
allowed-tools: [Bash(gh repo view*), Bash(gh project list*), Bash(gh project item-list*), Bash(gh issue view*), AskUserQuestion]
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
