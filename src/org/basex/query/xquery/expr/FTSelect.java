package org.basex.query.xquery.expr;

import static org.basex.query.xquery.XQText.*;
import static org.basex.util.Token.*;
import org.basex.query.xquery.XQContext;
import org.basex.query.xquery.XQException;
import org.basex.query.xquery.item.Dbl;
import org.basex.query.xquery.item.Item;
import org.basex.query.xquery.iter.Iter;
import org.basex.query.xquery.util.Err;
import org.basex.util.Array;
import org.basex.util.IntList;
import org.basex.util.TokenList;

/**
 * FTSelect expression.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class FTSelect extends Single implements Cloneable {
  /** Word unit. */
  public static final int WORDS = 1;
  /** Sentences unit. */
  public static final int SENTENCES = 2;
  /** Paragraphs unit. */
  public static final int PARAGRAPHS = 3;

  /** Ordered flag. */
  public boolean ordered;
  /** Start flag. */
  public boolean start;
  /** End flag. */
  public boolean end;
  /** Entire content flag. */
  public boolean content;
  /** Window. */
  public Expr window;
  /** Window unit. */
  public int wunit;
  /** Distance occurrences. */
  public Expr[] dist;
  /** Distance unit. */
  public int dunit;
  /** Same flag. */
  public boolean same;
  /** Different flag. */
  public boolean different;
  /** Same/different unit. */
  public int sdunit;
  /** Weight. */
  public Expr weight;

  /** Term list. */
  private TokenList term = new TokenList();
  /** Position list. */
  private IntList[] pos = new IntList[0];
  /** Number of entries. */
  private int size;

  /**
   * Constructor.
   * @param e expression
   */
  public FTSelect(final Expr e) {
    super(e);
  }

  @Override
  public Iter iter(final XQContext ctx) throws XQException {
    final FTSelect tmp = ctx.ftselect;
    ctx.ftselect = this;
    size = 0;
    term.reset();

    final Item it = ctx.iter(expr).next();
    ctx.ftselect = tmp;
    if(size == 0) return Dbl.iter(it.dbl());
    
    if(ordered) {
      int c = -1;
      int d = -1;
      for(int i = 0; i < size; i++) {
        for(int j = 0; j < pos[i].size; j++) {
          d = pos[i].get(j);
          if(c <= d) break;
        }
        if(c > d) return Dbl.iter(0);
        c = d;
      }
    }

    // ...to be revised...
    if(start || end || content) {
      final int c = words(ctx.ftitem);
      int l = 0;
      if(start || content) {
        for(int i = 0; i < size; i++) {
          boolean o = false;
          for(int j = 0; j < pos[i].size; j++) {
            if(pos[i].get(j) == l) {
              l += words(term.list[i]);
              o = true;
              break;
            }
          }
          if(!o) return Dbl.iter(0);
        }
      }
      if(content && l != c) return Dbl.iter(0);

      if(end) {
        for(int i = 0; i < size; i++) l += words(term.list[i]);
        for(int i = 0; i < size; i++) {
          boolean o = false;
          for(int j = 0; j < pos[i].size; j++) {
            if(l + pos[i].get(j) == c) {
              o = true;
              break;
            }
          }
          if(!o) return Dbl.iter(0);
        }
      }
    }

    // ...to be revised...
    if(dunit != 0) {
      final long mn = checkItr(ctx.iter(dist[0]));
      final long mx = checkItr(ctx.iter(dist[1]));
      int l = -1;
      for(int i = 0; i < size; i++) {
        boolean o = false;
        for(int j = 0; j < pos[i].size; j++) {
          final int p = calc(ctx, pos[i].get(j), dunit);
          if(i != 0 && (mn <= p - l && mx >= p - l)) {
            o = true;
            l = p;
            break;
          }
        }
        if(!o) return Dbl.iter(0);
      }
    }

    // ...to be revised...
    if(wunit != 0) {
      final long c = checkItr(ctx.iter(window));
      int l = -1;
      for(int i = 0; i < size; i++) {
        boolean o = false;
        for(int j = 0; j < pos[i].size; j++) {
          final int p = calc(ctx, pos[i].get(j), wunit);
          if(i != 0 && (Math.abs(p - l) < c)) {
            o = true;
            l = p;
            break;
          }
        }
        if(!o) return Dbl.iter(0);
      }
    }

    if(same) {
      final IntList il = pos[0];
      int p = -1, q = 0;
      for(int i = 0; i < il.size && p != q; i++) {
        p = calc(ctx, il.get(i), sdunit);
        q = p;
        for(int j = 1; j < size && p == q; j++) {
          for(int k = 0; k < pos[j].size; k++) {
            q = calc(ctx, pos[j].get(k), sdunit);
            if(p == q) break;
          }
        }
      }
      if(p != q) return Dbl.iter(0);
    }

    // ...to be revised...
    if(different) {
      int l = -1;
      for(int i = 0; i < size; i++) {
        boolean o = false;
        for(int j = 0; j < pos[i].size; j++) {
          final int p = calc(ctx, pos[i].get(j), sdunit);
          if(i != 0 && p != l) {
            o = true;
            break;
          }
          l = p;
        }
        if(i != 0 && !o) return Dbl.iter(0);
      }
    }

    final double d = checkDbl(ctx.iter(weight));
    if(d < 0 || d > 1000) Err.or(FTWEIGHT, d);
    return d != 1 ? Dbl.iter(it.dbl() * d) : it.iter();
  }

  /**
   * Adds a fulltext term.
   * @param t term to be added
   * @param il integer list to be added
   */
  void add(final byte[] t, final IntList il) {
    pos = Array.resize(pos, size, ++size);
    pos[size - 1] = il;
    term.add(t);
  }

  @Override
  public Expr comp(final XQContext ctx) throws XQException {
    weight = weight.comp(ctx);
    return super.comp(ctx);
  }

  /**
   * Calculates a new position value, dependent on the specified unit.
   * @param ctx query context
   * @param p position
   * @param u unit
   * @return new position
   */
  private int calc(final XQContext ctx, final int p, final int u) {
    if(u == SENTENCES) return sentence(ctx.ftitem, p);
    if(u == PARAGRAPHS) return paragraph(ctx.ftitem, p);
    return u;
  }

  /**
   * Returns the number of tokens of the specified item.
   * @param tok token
   * @return word position
   */
  private static int words(final byte[] tok) {
    final int tl = tok.length;

    // compare tokens character wise
    int p = 0;
    boolean l = false;
    for(int t = 0; t < tl; t++) {
      final boolean lod = letterOrDigit(tok[t]);
      if(!l && lod) p++;
      l = lod;
    }
    return p;
  }

  /**
   * Returns the sentence number for the specified position.
   * @param tok token
   * @param pos token position
   * @return word position
   */
  private static int sentence(final byte[] tok, final int pos) {
    final int tl = tok.length;

    int p = 0;
    int s = 0;
    boolean l = false;
    for(int t = 0; t < tl && p <= pos; t++) {
      final byte c = tok[t];
      final boolean ld = letterOrDigit(c);
      if(!l && ld) p++;
      if(c == '.' || c == '!' || c == '?') s++;
      l = ld;
    }
    return s;
  }

  /**
   * Returns the paragraph number for the specified position.
   * @param tok token
   * @param pos token position
   * @return word position
   */
  private static int paragraph(final byte[] tok, final int pos) {
    final int tl = tok.length;

    int p = 0;
    int s = 0;
    boolean l = false;
    for(int t = 0; t < tl && p < pos; t++) {
      final byte c = tok[t];
      final boolean ld = letterOrDigit(c);
      if(!l && ld) p++;
      if(c == '\n') s++;
      l = ld;
    }
    return s;
  }

  @Override
  public FTOptions clone() {
    try {
      return (FTOptions) super.clone();
    } catch(final CloneNotSupportedException e) {
      return null;
    }
  }

  @Override
  public String toString() {
    return expr.toString();
  }
}
