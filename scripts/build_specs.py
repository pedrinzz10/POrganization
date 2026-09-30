"""Gera specs.json a partir dos blocos JSON e da tabela de stack do PLANO_IMPLEMENTACAO.md.

Uso: python scripts/build_specs.py
Falha (exit 1) se alguma spec violar as regras do esquema do plano.
"""
import collections
import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLAN = ROOT / "PLANO_IMPLEMENTACAO.md"
OUT = ROOT / "specs.json"

ETAPAS = {
    "1-base": "Base",
    "2-compromissos": "Compromissos",
    "3-estudos": "Estudos",
    "4-financas": "Finanças",
    "5-integracoes": "Integrações (lembretes, e-mail e Google Calendar)",
    "6-tarefas": "Tarefas diárias",
    "7-design": "Design (Intelly Design System)",
}
STATUS = ["pendente", "em_andamento", "em_revisao", "concluida", "bloqueada"]
TIPOS = ["unitario", "integracao", "componente", "e2e"]
CAMPOS = ["id", "etapa", "titulo", "acao", "story", "arquivos", "dependencias", "status",
          "criterios_de_aceite", "testes_dos_criterios"]


def slug(text):
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def parse_stack(md):
    section = md.split("## 1.", 1)[1].split("\n## ", 1)[0]
    rows = re.findall(r"^\| (.+?) \| (.+?) \|$", section, re.M)
    return {slug(k): v.replace("`", "").replace("**", "")
            for k, v in rows if k not in ("Item",) and not k.startswith("---")}


def parse_line(md, label):
    m = re.search(rf"^{label}: `(.+?)`", md, re.M)
    return m.group(1) if m else None


def validate(specs):
    errors = []
    ids = [s["id"] for s in specs]
    errors += [f"id duplicado: {i}" for i, c in collections.Counter(ids).items() if c > 1]
    known = set(ids)
    for s in specs:
        sid = s.get("id", "?")
        errors += [f"{sid}: sem campo {f}" for f in CAMPOS if f not in s]
        if s.get("etapa") not in ETAPAS:
            errors.append(f"{sid}: etapa desconhecida {s.get('etapa')}")
        if s.get("status") not in STATUS:
            errors.append(f"{sid}: status inválido {s.get('status')}")
        errors += [f"{sid}: depende de spec inexistente {d}" for d in s.get("dependencias", []) if d not in known]
        cas = {c["id"] for c in s.get("criterios_de_aceite", [])}
        testados = {t["criterio"] for t in s.get("testes_dos_criterios", [])}
        errors += [f"{sid}: {c} sem teste" for c in sorted(cas - testados)]
        errors += [f"{sid}: teste aponta para critério inexistente {c}" for c in sorted(testados - cas)]
        errors += [f"{sid}: {t['id']} com tipo inválido {t['tipo']}"
                   for t in s.get("testes_dos_criterios", []) if t["tipo"] not in TIPOS]
    return errors


def main():
    md = PLAN.read_text(encoding="utf-8")
    specs = []
    for block in re.findall(r"```json\n(\[\n.*?)```", md, re.S):
        specs.extend(json.loads(block))

    errors = validate(specs)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        sys.exit(1)

    etapas = collections.OrderedDict()
    for s in specs:
        etapas.setdefault(s["etapa"], []).append(s["id"])

    out = {
        "projeto": "POrganization",
        "repositorio": "https://github.com/pedrinzz10/POrganization",
        "fonte": PLAN.name,
        "stack": parse_stack(md),
        "pacotes_backend": parse_line(md, "Pacotes do backend"),
        "pastas_frontend": parse_line(md, "Pastas do frontend"),
        "regras": {
            "status": STATUS,
            "tipos_de_teste": TIPOS,
            "todo_criterio_tem_teste": True,
            "uma_spec_um_branch_um_pr": True,
        },
        "etapas": [{"id": k, "nome": ETAPAS[k], "specs": v} for k, v in etapas.items()],
        "total_specs": len(specs),
        "total_criterios": sum(len(s["criterios_de_aceite"]) for s in specs),
        "specs": specs,
    }
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"{OUT.name}: {out['total_specs']} specs, {out['total_criterios']} critérios")


if __name__ == "__main__":
    main()
