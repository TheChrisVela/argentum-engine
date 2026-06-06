package com.wingedsheep.tooling.coverage

/**
 * The emitter's typed OUTPUT model — a tiny AST for the Argentum cardDef DSL the emitter generates.
 *
 * The emitter translates the (untyped, irregular) mtgish IR into Argentum DSL *source*. That output is
 * a single, regular language, so it earns a typed tree: handlers build [Dsl] nodes instead of
 * interpolating strings, and [render] is the ONE place a node becomes text. This kills the per-handler
 * `"Name($a, $b)"` string-stitching (every comma/quote/paren hand-placed) in favour of structural
 * construction that's unit-testable without asserting on whitespace.
 *
 * Note this models the OUTPUT only — it is NOT a shared IR between mtgish and Argentum, so the lossy,
 * many-to-one mtgish→Argentum mapping stays where it belongs: inside the handlers, which decide WHICH
 * node to build. A shape with no faithful Argentum rendering still returns null (→ SCAFFOLD).
 *
 * [render] reproduces the emitter's historical *pre-wrap* line strings exactly: a [Call] is one line,
 * only [Composite] is multi-line (mirroring the old `composite()` helper). The downstream
 * `Shells.assemble` pass (`wrapLine` + `importsFor`) then reflows long lines and derives imports as
 * before — so swapping a handler from string-building to node-building is a behaviour-free change,
 * provable against the committed golden. [Raw] is the escape hatch for an already-formatted fragment a
 * node doesn't model yet (e.g. the hand-indented look-and-distribute literal).
 */
sealed interface Dsl

/** A literal token rendered verbatim: an enum ref (`Color.WHITE`), an int, the bound `t` target var,
 *  a string literal, or any sub-expression still carried as text. */
data class Lit(val text: String) : Dsl

/** A call/constructor expression `callee(arg, name = arg, …)`. [callee] may be dotted
 *  (`Effects.DrawCards`, `Patterns.Library.surveil`). No args → `callee()`. */
data class Call(val callee: String, val args: List<Arg> = emptyList()) : Dsl

/** A receiver with chained `.method(args)` links: `base.withSubtype("Goblin").tapped()`. */
data class Chain(val base: Dsl, val links: List<Link>) : Dsl

/** One `.method(args)` link in a [Chain]. */
data class Link(val method: String, val args: List<Arg> = emptyList())

/** An infix chain `a op b op c`, optionally wrapped in parens (the filter `or`). */
data class Infix(val op: String, val parts: List<Dsl>, val parenthesized: Boolean = false) : Dsl

/** `Effects.Composite(...)` with one element per line — the one intrinsically multi-line node, matching
 *  the historical `composite()` layout (12-space element indent, 8-space close). Callers pass ≥2 parts. */
data class Composite(val parts: List<Dsl>) : Dsl

/** Verbatim, already-formatted source for a shape no node models yet. The pressure-relief valve that
 *  lets the tree coexist with un-migrated output; such fragments still flow through `wrapLine`/`importsFor`. */
data class Raw(val text: String) : Dsl

/** A positional (name == null) or named (`name = value`) argument. */
data class Arg(val value: Dsl, val name: String? = null)

// ---------------------------------------------------------------------------
// Statement / block layer — the card-body structure above the expression nodes.
// An expression [Dsl] renders to ONE token/line; a [Stmt]/[Block] renders to indented source LINES
// (an ability builder block, the metadata block, the whole `card(...) { }`). This is what lets the
// emitter assemble a card as a single tree instead of stitching `List<String>` line lists.
// ---------------------------------------------------------------------------

/** One line (or builder block) inside a [Block] body. */
sealed interface Stmt

/** `name = <value>` (e.g. `effect = …`, `cost = …`, `auraTarget = …`). */
data class Assign(val name: String, val value: Dsl) : Stmt

/** `val name = <value>` (e.g. `val t = target("target", …)`). */
data class Local(val name: String, val value: Dsl) : Stmt

/** A bare expression statement: `<value>` (e.g. `keywordAbility(…)`, `flags(…)`, `dynamicStats(…)`). */
data class Eval(val value: Dsl) : Stmt

/** A nested builder block as a statement (`spell { … }`, `staticAbility { … }`, `metadata { … }`). */
data class Sub(val block: Block) : Stmt

/** A pre-formatted verbatim line — the shell scaffolding (mana/typeline/KDoc/metadata text) and the
 *  occasional multi-line restriction list a node doesn't model yet. The [Stmt]-level [Raw]. */
data class RawLine(val text: String) : Stmt

/** A `header { body }` builder block (an ability, the metadata block, the whole card). */
data class Block(val header: String, val body: List<Stmt>)

/** Render a [Block] to source lines at [indent]; the body sits one level (4 spaces) deeper. */
fun renderBlock(block: Block, indent: String = ""): List<String> {
    val out = mutableListOf("$indent${block.header} {")
    block.body.forEach { out += renderStmt(it, "$indent    ") }
    out += "$indent}"
    return out
}

/** Render one [Stmt] to source lines at [indent]. A [RawLine] is emitted verbatim (it already carries
 *  its own indentation); everything else is placed at [indent]. */
fun renderStmt(stmt: Stmt, indent: String): List<String> = when (stmt) {
    is Assign -> listOf("$indent${stmt.name} = ${render(stmt.value)}")
    is Local -> listOf("${indent}val ${stmt.name} = ${render(stmt.value)}")
    is Eval -> listOf("$indent${render(stmt.value)}")
    is Sub -> renderBlock(stmt.block, indent)
    is RawLine -> listOf(stmt.text)
}

// ---------------------------------------------------------------------------
// Construction helpers — keep handler call-sites terse.
// ---------------------------------------------------------------------------

/** A positional argument from a node. */
fun arg(value: Dsl): Arg = Arg(value)

/** A positional argument from a literal token. */
fun arg(text: String): Arg = Arg(Lit(text))

/** A named argument from a node. */
fun arg(name: String, value: Dsl): Arg = Arg(value, name)

/** A named argument from a literal token. */
fun arg(name: String, text: String): Arg = Arg(Lit(text), name)

/** `callee(args…)` from inline [Arg]s. */
fun call(callee: String, vararg args: Arg): Call = Call(callee, args.toList())

/** `base.method(args…)` — a single-link chain. */
fun Dsl.dot(method: String, vararg args: Arg): Chain = dot(Link(method, args.toList()))

/** Append a pre-built [Link] to this receiver as a chain. */
fun Dsl.dot(link: Link): Chain =
    if (this is Chain) Chain(base, links + link) else Chain(this, listOf(link))

// ---------------------------------------------------------------------------
// Rendering — the single node → pre-wrap source text mapping.
// ---------------------------------------------------------------------------

/** Render a node to the emitter's historical pre-wrap line string (one line except [Composite]). */
fun render(node: Dsl): String = when (node) {
    is Lit -> node.text
    is Raw -> node.text
    is Call -> node.callee + "(" + node.args.joinToString(", ") { renderArg(it) } + ")"
    is Chain -> render(node.base) + node.links.joinToString("") { link ->
        "." + link.method + "(" + link.args.joinToString(", ") { renderArg(it) } + ")"
    }
    is Infix -> {
        val joined = node.parts.joinToString(" ${node.op} ") { render(it) }
        if (node.parenthesized) "($joined)" else joined
    }
    // Matches the old `composite()`: prefix, each element at 12 spaces, comma-separated, 8-space close.
    is Composite -> node.parts.joinToString(
        ",\n", prefix = "Effects.Composite(\n", postfix = "\n        )",
    ) { "            ${render(it)}" }
}

private fun renderArg(a: Arg): String = (a.name?.let { "$it = " } ?: "") + render(a.value)
