// Renders the adaptive launcher icon (res/drawable/ic_launcher_background.xml + ic_launcher_foreground.xml) into
// classic PNG icons for every density (mipmap-*dpi/ic_launcher_bitmap.png and ic_launcher_bitmap_round.png).
// Why: launchers use the adaptive icon (mipmap-anydpi-v26), but file managers, APK viewers and some package installers
// read the application icon from the APK and showed a blank icon when it was only vector XML. The manifest uses these
// bitmaps as the application icon and keeps the adaptive icon on the launcher activity.
// Run after changing the icon vectors:  powershell -File tools\launcher-icons\Render-LauncherIcons.ps1
// Supports the vector subset these files use: group scale/pivot/translate, path M L H V Z A Q C (absolute and relative),
// solid and linear-gradient fills, fill/stroke alpha, stroke width, round caps and joins. C# 5 (.NET Framework csc).
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Xml.Linq;

static class LauncherIcons
{
    static readonly XNamespace A = "http://schemas.android.com/apk/res/android";
    static readonly XNamespace Aapt = "http://schemas.android.com/aapt";
    static readonly CultureInfo Inv = CultureInfo.InvariantCulture;

    static int Main(string[] args)
    {
        if (args.Length != 1) { Console.Error.WriteLine("Usage: LauncherIcons <app/src/main/res>"); return 2; }
        string res = args[0];
        var layers = new[] { "ic_launcher_background.xml", "ic_launcher_foreground.xml" }.Select(n => XDocument.Load(Path.Combine(res, "drawable", n)).Root).ToArray();
        var densities = new Dictionary<string, int> { { "mdpi", 48 }, { "hdpi", 72 }, { "xhdpi", 96 }, { "xxhdpi", 144 }, { "xxxhdpi", 192 } };
        foreach (var d in densities)
        {
            string dir = Path.Combine(res, "mipmap-" + d.Key);
            Directory.CreateDirectory(dir);
            using (var full = Render(layers, d.Value))
            {
                Mask(full, false).Save(Path.Combine(dir, "ic_launcher_bitmap.png"), ImageFormat.Png);
                Mask(full, true).Save(Path.Combine(dir, "ic_launcher_bitmap_round.png"), ImageFormat.Png);
            }
            Console.WriteLine("mipmap-" + d.Key + ": " + d.Value + " px");
        }
        return 0;
    }

    /// <summary>Draws the layers' visible centre (the 72 dp of the 108 dp canvas that launchers show) at size×size.</summary>
    static Bitmap Render(XElement[] layers, int size)
    {
        int scale = 4; // Supersample, then downscale for clean edges.
        var big = new Bitmap(size * scale, size * scale, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(big))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            float k = size * scale / 72f;
            var root = new Matrix();
            root.Scale(k, k);
            root.Translate(-18, -18);
            foreach (var layer in layers) DrawGroup(g, layer, root);
        }
        var small = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(small))
        {
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.DrawImage(big, new Rectangle(0, 0, size, size));
        }
        big.Dispose();
        return small;
    }

    static Bitmap Mask(Bitmap source, bool round)
    {
        int n = source.Width;
        var output = new Bitmap(n, n, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(output))
        using (var brush = new TextureBrush(source))
        using (var path = new GraphicsPath())
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            float m = n / 48f; // 1 px margin at mdpi, like Android Studio's legacy icons.
            var r = new RectangleF(m, m, n - 2 * m, n - 2 * m);
            if (round) path.AddEllipse(r);
            else
            {
                float c = r.Width * 0.18f * 2;
                path.AddArc(r.X, r.Y, c, c, 180, 90);
                path.AddArc(r.Right - c, r.Y, c, c, 270, 90);
                path.AddArc(r.Right - c, r.Bottom - c, c, c, 0, 90);
                path.AddArc(r.X, r.Bottom - c, c, c, 90, 90);
                path.CloseFigure();
            }
            g.FillPath(brush, path);
        }
        return output;
    }

    static float F(XElement e, string name, float fallback)
    {
        var a = e.Attribute(A + name);
        return a == null ? fallback : float.Parse(a.Value, Inv);
    }

    static void DrawGroup(Graphics g, XElement group, Matrix parent)
    {
        foreach (var child in group.Elements())
        {
            if (child.Name.LocalName == "group")
            {
                // Android order: translate * pivot * scale * -pivot (no rotation used here).
                var m = parent.Clone();
                float px = F(child, "pivotX", 0), py = F(child, "pivotY", 0);
                m.Translate(F(child, "translateX", 0) + px, F(child, "translateY", 0) + py);
                m.Scale(F(child, "scaleX", 1), F(child, "scaleY", 1));
                m.Translate(-px, -py);
                DrawGroup(g, child, m);
            }
            else if (child.Name.LocalName == "path") DrawPath(g, child, parent);
        }
    }

    static Color ColorOf(string text, float alpha)
    {
        string hex = text.TrimStart('#');
        uint v = uint.Parse(hex, NumberStyles.HexNumber);
        if (hex.Length == 6) v |= 0xFF000000;
        int a = (int)Math.Round(((v >> 24) & 0xFF) * alpha);
        return Color.FromArgb(a, (int)((v >> 16) & 0xFF), (int)((v >> 8) & 0xFF), (int)(v & 0xFF));
    }

    static void DrawPath(Graphics g, XElement p, Matrix transform)
    {
        using (var path = Parse(p.Attribute(A + "pathData").Value))
        {
            var state = g.Save();
            g.Transform = transform;
            float fillAlpha = F(p, "fillAlpha", 1), strokeAlpha = F(p, "strokeAlpha", 1);
            var fillAttr = p.Attribute(A + "fillColor");
            var gradient = p.Elements(Aapt + "attr").Where(x => (string)x.Attribute("name") == "android:fillColor").Select(x => x.Element("gradient")).FirstOrDefault();
            if (gradient != null)
            {
                var start = new PointF(F(gradient, "startX", 0), F(gradient, "startY", 0));
                var end = new PointF(F(gradient, "endX", 0), F(gradient, "endY", 1));
                var items = gradient.Elements("item").Select(i => new { o = F(i, "offset", 0), c = ColorOf(i.Attribute(A + "color").Value, fillAlpha) }).ToList();
                using (var brush = new LinearGradientBrush(start, end, items.First().c, items.Last().c) { WrapMode = WrapMode.TileFlipXY })
                {
                    var blend = new ColorBlend { Colors = items.Select(i => i.c).ToArray(), Positions = items.Select(i => i.o).ToArray() };
                    blend.Positions[0] = 0;
                    blend.Positions[blend.Positions.Length - 1] = 1;
                    brush.InterpolationColors = blend;
                    g.FillPath(brush, path);
                }
            }
            else if (fillAttr != null)
                using (var brush = new SolidBrush(ColorOf(fillAttr.Value, fillAlpha))) g.FillPath(brush, path);
            var strokeAttr = p.Attribute(A + "strokeColor");
            if (strokeAttr != null)
                using (var pen = new Pen(ColorOf(strokeAttr.Value, strokeAlpha), F(p, "strokeWidth", 1)))
                {
                    if ((string)p.Attribute(A + "strokeLineCap") == "round") { pen.StartCap = LineCap.Round; pen.EndCap = LineCap.Round; }
                    if ((string)p.Attribute(A + "strokeLineJoin") == "round") pen.LineJoin = LineJoin.Round;
                    g.DrawPath(pen, path);
                }
            g.Restore(state);
        }
    }

    /// <summary>SVG path data subset → GraphicsPath.</summary>
    static GraphicsPath Parse(string data)
    {
        var path = new GraphicsPath();
        var tokens = Regex.Matches(data, @"[MmLlHhVvZzAaQqCc]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?").Cast<Match>().Select(m => m.Value).ToList();
        int i = 0;
        char cmd = ' ';
        PointF cur = PointF.Empty, start = PointF.Empty;
        Func<float> num = () => float.Parse(tokens[i++], Inv);
        while (i < tokens.Count)
        {
            if (Char.IsLetter(tokens[i][0])) cmd = tokens[i++][0];
            bool rel = Char.IsLower(cmd);
            Func<float, float, PointF> pt = (x, y) => rel ? new PointF(cur.X + x, cur.Y + y) : new PointF(x, y);
            switch (Char.ToUpperInvariant(cmd))
            {
                case 'M':
                    path.StartFigure();
                    cur = start = pt(num(), num());
                    cmd = rel ? 'l' : 'L'; // Further pairs are line-tos.
                    break;
                case 'L': { var p = pt(num(), num()); path.AddLine(cur, p); cur = p; break; }
                case 'H': { float x = num(); var p = new PointF(rel ? cur.X + x : x, cur.Y); path.AddLine(cur, p); cur = p; break; }
                case 'V': { float y = num(); var p = new PointF(cur.X, rel ? cur.Y + y : y); path.AddLine(cur, p); cur = p; break; }
                case 'Z': path.CloseFigure(); cur = start; break;
                case 'Q':
                    {
                        var c = pt(num(), num()); var p = pt(num(), num());
                        path.AddBezier(cur, new PointF(cur.X + 2f / 3 * (c.X - cur.X), cur.Y + 2f / 3 * (c.Y - cur.Y)), new PointF(p.X + 2f / 3 * (c.X - p.X), p.Y + 2f / 3 * (c.Y - p.Y)), p);
                        cur = p;
                        break;
                    }
                case 'C': { var c1 = pt(num(), num()); var c2 = pt(num(), num()); var p = pt(num(), num()); path.AddBezier(cur, c1, c2, p); cur = p; break; }
                case 'A':
                    {
                        float rx = num(), ry = num(); num(); // x-axis rotation: unused (0) in these icons
                        bool large = num() != 0, sweep = num() != 0;
                        var p = pt(num(), num());
                        Arc(path, cur, p, rx, ry, large, sweep);
                        cur = p;
                        break;
                    }
                default: throw new FormatException("Unsupported path command " + cmd);
            }
        }
        return path;
    }

    /// <summary>SVG endpoint arc (no rotation) to a GraphicsPath arc, via the centre parameterisation.</summary>
    static void Arc(GraphicsPath path, PointF p0, PointF p1, float rx, float ry, bool large, bool sweep)
    {
        double x1p = (p0.X - p1.X) / 2.0, y1p = (p0.Y - p1.Y) / 2.0;
        double lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry);
        if (lambda > 1) { rx *= (float)Math.Sqrt(lambda); ry *= (float)Math.Sqrt(lambda); }
        double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double coef = (large == sweep ? -1 : 1) * Math.Sqrt(Math.Max(0, num / den));
        double cxp = coef * rx * y1p / ry, cyp = -coef * ry * x1p / rx;
        double cx = cxp + (p0.X + p1.X) / 2.0, cy = cyp + (p0.Y + p1.Y) / 2.0;
        double a1 = Math.Atan2((y1p - cyp) / ry, (x1p - cxp) / rx);
        double a2 = Math.Atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx);
        double delta = a2 - a1;
        if (sweep && delta < 0) delta += 2 * Math.PI;
        if (!sweep && delta > 0) delta -= 2 * Math.PI;
        path.AddArc((float)(cx - rx), (float)(cy - ry), 2 * rx, 2 * ry, (float)(a1 * 180 / Math.PI), (float)(delta * 180 / Math.PI));
    }
}
