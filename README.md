# Agent Skills by Cosimo Eugenio Bortone

A collection of reusable [Agent Skills](https://agentskills.io/) for AI coding agents.

## Available Skills

| Skill | Description | Version |
|-------|-------------|---------|
| [java-spring-best-practices](./java-spring-best-practices/) | Enterprise-grade Spring Boot 3.5.x/4.x guide | v2.0.0 |

## Installation

Install all skills from this repo:

```bash
npx skills add cosbort/agent-skills
```

Or install a specific skill:

```bash
npx skills add cosbort/agent-skills/java-spring-best-practices
```

### Manual installation

Clone or copy the skill folder into your project or user skills directory:

```bash
# For Claude Code (project-level)
cp -r java-spring-best-practices .claude/skills/

# For Claude Code (global, all projects)
cp -r java-spring-best-practices ~/.claude/skills/

# For cross-agent compatibility (works with Copilot, Codex, Cursor, etc.)
cp -r java-spring-best-practices ~/.agents/skills/
```

## Compatibility

These skills follow the [Agent Skills specification](https://agentskills.io/) and work with:

- **Claude Code** — Anthropic's CLI coding agent
- **Claude.ai** — Via custom skills upload
- **GitHub Copilot** — VS Code / JetBrains
- **OpenAI Codex** — Via `.agents/skills/` convention
- **Cursor, Windsurf, Cline, Roo** — Any agent supporting the open spec

## Contributing

Contributions are welcome! If you want to improve an existing skill or add a new one:

1. Fork this repo
2. Create your feature branch (`git checkout -b feature/my-skill`)
3. Follow the [Agent Skills spec](https://agentskills.io/) for SKILL.md structure
4. Submit a pull request

## Author

**Cosimo Eugenio Bortone** — Software Development Specialist at Engineering Ingegneria Informatica S.p.A.

- GitHub: [@cosbort](https://github.com/cosbort)

## License

MIT — see [LICENSE](./LICENSE) for details.
