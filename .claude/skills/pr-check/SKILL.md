---
name: pr-check
description: Roda os testes do backend e/ou frontend e revisa o código alterado no branch atual antes de abrir uma PR. Use quando o usuário rodar /pr-check, pedir para "rodar os testes antes da PR", "verificar se está pronto pra PR", ou revisar as mudanças antes de abrir um pull request.
argument-hint: "[branch base opcional, padrão main]"
allowed-tools: [Bash(git*), Bash(mvn*), Bash(npm*), Bash(gh*), Read, Grep, Glob, Skill, AskUserQuestion]
---

# /pr-check — Testar e revisar antes de abrir PR

Gate de qualidade a rodar **antes** de abrir uma PR neste repo. Faz três coisas, nesta
ordem, e só recomenda abrir a PR se as três passarem: (1) roda os testes relevantes,
(2) revisa o diff em busca de bugs/simplificações, (3) confere as mudanças contra as
regras de modularidade específicas deste projeto (`README.md` → "Contrato de
Modularidade"), que uma revisão genérica não conhece.

Não abra, não faça push nem crie a PR sozinho — este skill só reporta o resultado e
pergunta como o usuário quer seguir.

## 0. Escopo do diff

Branch base: `$ARGUMENTS` se fornecido, senão `main` (branch principal deste repo,
conforme `CLAUDE.md`).

```bash
git status
git fetch origin main --quiet 2>/dev/null || true
git merge-base --is-ancestor <base> HEAD 2>/dev/null || true
git diff <base>...HEAD --stat
git diff <base>...HEAD --name-only
```

Se houver mudanças não commitadas (`git status` sujo), inclua-as no escopo da revisão
(rode `git diff` sem range também) mas avise o usuário que elas ainda não foram
commitadas.

Classifique os arquivos alterados:
- **backend**: qualquer coisa em `backend/`
- **frontend**: qualquer coisa em `frontend/` (fora de `design-reference/`)
- **infra/outros**: `docker-compose.yml`, `.env.example`, `README.md`, etc.

Se nada mudou em relação à base, informe isso e pare — não há o que testar ou revisar.

## 1. Rodar os testes

Só rode a suíte de um lado se houve mudança nesse lado (ou se o usuário pediu
explicitamente para rodar tudo).

**Backend** (Maven — inclui os testes ArchUnit que impõem o "Contrato de
Modularidade" descrito no README):
```bash
cd backend && mvn -q test
```

**Frontend** — hoje não há framework de teste configurado (`frontend/package.json` só
tem `lint`, `build`, `dev`, `preview`). Rode o que existe como gate de qualidade:
```bash
cd frontend && npm run lint
cd frontend && npm run build
```
Se as mudanças de frontend adicionaram lógica não-trivial (hooks, funções de
transformação de dados, etc.) e não há testes automatizados cobrindo isso, sinalize
isso como lacuna no resumo final — não instale um test runner novo por conta própria
sem o usuário pedir.

Se qualquer comando falhar, **pare de tratar como bloqueio**: mostre a falha
relevante (não o log inteiro) e vá direto para o resumo final marcando o teste como
FALHOU. Ainda vale continuar para os passos 2 e 3 se for rápido, para dar um panorama
completo de uma vez — mas deixe claro que a PR não deve ser aberta com testes
quebrados.

## 2. Revisão de código

Delegue a revisão geral (bugs de corretude, simplificação, reuso, eficiência) ao skill
`code-review` já existente neste repo, sobre o mesmo diff/branch:

```
Skill(skill: "code-review")
```

Deixe o nível de esforço no padrão dele (reaproveita o último usado, ou o padrão do
skill) a menos que o usuário peça um nível específico.

## 3. Checklist do Contrato de Modularidade

Além da revisão genérica, confira manualmente os arquivos Java alterados (`git diff
<base>...HEAD -- 'backend/**/*.java'`) contra as 6 regras do README
(seção "Contrato de Modularidade"). Para cada arquivo alterado/novo em
`backend/src/main/java/com/mercatto/<modulo>/...`:

1. **Sem relação JPA cross-module**: nenhum `@ManyToOne`/`@OneToMany`/`@JoinColumn`
   apontando para entidade de outro módulo. Referências cross-module devem ser um id
   simples (`Long`).
2. **Sem transação cruzando módulos**: um método `@Transactional` não deve chamar
   método de *mutação* do `service` de outro módulo dentro da mesma transação — isso
   deveria ser um `ApplicationEvent` (`@TransactionalEventListener(phase =
   AFTER_COMMIT)`).
3. **Só `service`/`event` são API pública**: nenhuma classe de um módulo deve
   importar `<outromodulo>.repository.*` ou a entidade de outro módulo diretamente.
   Implementações (`*ServiceImpl`) devem ser package-private quando possível.
4. **Integrações externas são ports**: nenhuma lógica de negócio deve importar SDK de
   terceiro (Stripe, gateway de pagamento, etc.) diretamente — deve haver uma
   interface (`service.PaymentGateway`-style) com mock/stub.
5. **Uma tabela, um schema**: entidades novas usam `@Table(schema = "<modulo>")`
   coerente com o módulo dono, nunca `public` nem schema de outro módulo.
6. **Fronteira = pacote**: nenhuma classe foi movida para dentro do pacote de outro
   módulo só para "facilitar" o acesso.

Use `grep`/`Read` nos arquivos do diff para checar isso — não é necessário ler o
módulo inteiro, só o que mudou e os imports que ele referencia. Se algo violar uma
regra, cite arquivo:linha.

## 4. Resumo final

Apresente um resumo curto e direto, nesta ordem:

- **Testes**: backend (passou/falhou/não rodou — motivo), frontend
  lint+build (idem), lacunas de cobertura relevantes.
- **Revisão de código**: principais achados do `code-review` (ou "nenhum achado").
- **Contrato de Modularidade**: violações encontradas (arquivo:linha + regra) ou "sem
  violações".
- **Veredito**: pronto para abrir PR, ou lista do que precisa ser corrigido antes.

Não abra a PR, não dê `git push` nem `gh pr create` automaticamente — pergunte ao
usuário como quer seguir (corrigir agora, abrir mesmo assim, etc.), a menos que ele já
tenha pedido explicitamente para abrir a PR ao chamar este skill.
