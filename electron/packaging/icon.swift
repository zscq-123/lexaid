// Render the LexAid book mark at each native macOS icon size.
import AppKit
import CoreGraphics

let directory = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
for scale in [1, 2] {
    for size in [16, 32, 128, 256, 512] {
        let pixels = size * scale
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        let ctx = CGContext(data: nil, width: pixels, height: pixels, bitsPerComponent: 8,
                            bytesPerRow: pixels * 4, space: colorSpace,
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.scaleBy(x: CGFloat(pixels)/1024, y: CGFloat(pixels)/1024)
        let contour = CGPath(roundedRect: CGRect(x: 92, y: 100, width: 840, height: 840),
                             cornerWidth: 187, cornerHeight: 187, transform: nil)
        ctx.saveGState()
        ctx.setShadow(offset: CGSize(width: 0, height: -10), blur: 18,
                      color: CGColor(srgbRed: 0.03, green: 0.1, blue: 0.22, alpha: 0.25))
        ctx.addPath(contour); ctx.setFillColor(CGColor(srgbRed: 0.12,green: 0.35,blue: 0.79,alpha: 1)); ctx.fillPath()
        ctx.restoreGState()
        ctx.addPath(contour); ctx.clip()
        let shades = [CGColor(srgbRed: 0.16, green: 0.49, blue: 0.96, alpha: 1),
                      CGColor(srgbRed: 0.10, green: 0.27, blue: 0.69, alpha: 1)]
        let blue = CGGradient(colorsSpace: colorSpace, colors: shades as CFArray, locations: [0,1])!
        ctx.drawLinearGradient(blue, start: .init(x: 240,y: 940), end: .init(x: 710,y: 100),
                               options: [.drawsBeforeStartLocation,.drawsAfterEndLocation])
        ctx.setStrokeColor(CGColor(gray: 1,alpha: 1)); ctx.setLineWidth(32)
        ctx.setLineJoin(.round); ctx.setLineCap(.round)
        let book = CGMutablePath()
        book.move(to: .init(x: 512,y: 344))
        book.addQuadCurve(to: .init(x: 258,y: 373),control: .init(x: 408,y: 403))
        book.addLine(to: .init(x: 258,y: 676))
        book.addQuadCurve(to: .init(x: 512,y: 644),control: .init(x: 406,y: 706))
        book.addQuadCurve(to: .init(x: 766,y: 676),control: .init(x: 618,y: 706))
        book.addLine(to: .init(x: 766,y: 373))
        book.addQuadCurve(to: .init(x: 512,y: 344),control: .init(x: 616,y: 403))
        book.addLine(to: .init(x: 512,y: 644))
        ctx.addPath(book); ctx.strokePath()
        let image = NSBitmapImageRep(cgImage: ctx.makeImage()!)
        let suffix = scale == 2 ? "@2x" : ""
        try image.representation(using: .png, properties: [:])!
            .write(to: directory.appendingPathComponent("icon_\(size)x\(size)\(suffix).png"))
    }
}
