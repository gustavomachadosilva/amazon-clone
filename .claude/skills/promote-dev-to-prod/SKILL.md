---
name: promote-dev-to-prod
description: Atualiza a branch dev local, roda o gate de qualidade (pr-check: testes + revisão + Contrato de Modularidade) sobre o trabalho que será promovido e abre a PR de dev para prod. Use quando o usuário rodar /promote-dev-to-prod, pedir para "promover dev pra prod", "subir pra prod", "liberar pra produção" ou "abrir a PR de promoção dev → prod".
allowed-tools: [Bash(git*), Bash(gh*), Read, Skill, AskUserQuestion]
---

# /promote-dev-to-prod — Promover dev para prod

Este skill existe para garantir uma única coisa: **nenhum código chega em `prod` sem
ter sido testado e revisado**. `dev` é a branch de desenvolvimento (branch padrão do
repositório); `prod` é protegida e só aceita merge via Pull Request (ver `CLAUDE.md` →
"Fluxo de trabalho no GitHub"). Este skill automatiza o caminho: atualizar `dev` local
→ rodar `/pr-check` sobre o que será promovido → abrir a PR `dev` → `prod`.

Se o gate de qualidade falhar em qualquer ponto, **não abra a PR**. Pare, reporte o
que falhou e pergunte ao usuário como seguir.

## 0. Estado do repositório local

```bash
git status
```

Se houver mudanças não commitadas, **não descarte nada**: avise o usuário e pergunte
se quer commitar, stashar (`git stash -u`) ou abortar antes de trocar de branch. Só
prossiga com working tree limpa (ou com o aval do usuário).

## 1. Atualizar dev local

```bash
git fetch origin --quiet
git checkout dev
git pull origin dev
```

Se o `checkout` ou o `pull` falharem (branch `dev` não existe localmente, divergência,
conflito), pare e reporte o erro — não tente resolver com `reset --hard` ou `clean`
por conta própria.

## 2. Confirmar que há algo para promover

```bash
git fetch origin prod --quiet 2>/dev/null || true
git log origin/prod..dev --oneline
```

Se não houver commits em `dev` que ainda não estão em `prod`, informe isso ao usuário
e pare — não há nada para promover.

Também verifique se já existe uma PR aberta de `dev` para `prod`, para não duplicar:

```bash
gh pr list --base prod --head dev --state open
```

Se já existir, mostre o link ao usuário e pergunte se quer reaproveitá-la (pular para
o passo 5) ou seguir mesmo assim.

## 3. Rodar o gate de qualidade (pr-check)

Delegue ao skill `pr-check` já existente neste repo, comparando `dev` contra `prod`
(é esse o diff que efetivamente vai para produção):

```
Skill(skill: "pr-check", args: "prod")
```

Isso cobre, nesta ordem: testes (`mvn test` no backend, `lint`+`build` no frontend),
revisão de código (bugs/simplificação/reuso/eficiência) e o checklist do Contrato de
Modularidade.

**Importante:** o `pr-check` não troca de branch — ele roda os testes sobre o working
tree atual, que já é `dev` (checked out no passo 1). O argumento `"prod"` é só a
branch base usada para calcular o diff (`prod...dev`), para que a revisão de código e
o checklist de modularidade olhem exatamente o que vai ser promovido, em vez do
default do `pr-check` (que compararia contra `main`). Os testes em si sempre validam o
código real de `dev`, nunca o de `prod`.

## 4. Avaliar o resultado

- **Testes falharam** → pare. Não abra a PR. Reporte a falha e pergunte se o usuário
  quer corrigir agora ou cancelar a promoção.
- **Revisão de código encontrou bug/achado bloqueante**, ou **violação do Contrato de
  Modularidade** → pare. Reporte os achados com arquivo:linha e pergunte como seguir.
  Não abra a PR "mesmo assim" sem confirmação explícita do usuário — indicar que sabe
  dos riscos e quer prosseguir de qualquer forma.
- **Tudo passou** (ou o usuário confirmou explicitamente que quer prosseguir mesmo com
  ressalvas não-bloqueantes) → siga para o passo 5.

## 5. Abrir a PR de dev para prod

```bash
gh pr create --base prod --head dev --title "Promover dev para prod" --body "$(cat <<'EOF'
## Resumo
<liste os commits/mudanças relevantes de `git log origin/prod..dev --oneline`>

## Gate de qualidade (pr-check)
- Testes: <passou/falhou>
- Revisão de código: <resumo ou "sem achados">
- Contrato de Modularidade: <sem violações / lista>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Preencha o corpo com o resultado real do passo 3/4, não um placeholder genérico.

## 6. Fechar o loop

Retorne ao usuário o link da PR criada (ou reaproveitada) e um resumo de uma linha do
veredito do gate de qualidade. Não faça merge da PR — a promoção final para `prod` é
decisão do usuário (e do processo de review no GitHub).
