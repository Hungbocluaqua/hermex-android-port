import SwiftUI

public enum HermexLayoutContract {
    public static let hermexLogoAspectRatio: CGFloat = 643.0 / 185.0
    public static let sessionListLogoWidth: CGFloat = 160
    public static let topChromeCircleSize: CGFloat = 58
    public static let topChromeCompactCircleSize: CGFloat = 46
    public static let topChromeClusterSpacing: CGFloat = 0
    public static let sessionListUtilityIconSlotWidth: CGFloat = 28
    public static let sessionListUtilityIconSize: CGFloat = 21
    public static let sessionListUtilityRowSpacing: CGFloat = 2
    public static let sessionListUtilityRowMinimumHeight: CGFloat = 44
    public static let sessionListSelectorHeight: CGFloat = 68
    public static let sessionListRowActionSize: CGFloat = 54
    public static let sessionListRowSeparatorOpacity: Double = 0.12
    public static let chatToolbarActionSlotSize: CGFloat = 44
    public static let chatTopBarHorizontalPadding: CGFloat = 18
    public static let chatTopBarVerticalPadding: CGFloat = 10
    public static let chatTopBarHeight: CGFloat = 76
    public static let chatTranscriptHorizontalPadding: CGFloat = 14
    public static let chatTranscriptTopPadding: CGFloat = 8
    public static let chatTranscriptMessageSpacing: CGFloat = 10
    public static let composerContainerSpacing: CGFloat = 6
    public static let composerSurfaceHorizontalPadding: CGFloat = 16
    public static let composerSurfaceTopPadding: CGFloat = 2
    public static let composerSurfaceBottomPadding: CGFloat = 8
    public static let composerControlSpacing: CGFloat = 12
    public static let composerCornerRadiusCollapsed: CGFloat = 22
    public static let composerCornerRadiusExpanded: CGFloat = 26
    public static let composerTextInputMinimumHeight: CGFloat = 42
    public static let composerTextInputMaximumHeight: CGFloat = 96
    public static let composerTextInputCollapsedContentHeight: CGFloat = 22
    public static let composerTextInputLineHeight: CGFloat = 22
    public static let composerTextInputWrapColumn: Int = 44
    public static let composerTextVerticalPaddingCollapsed: CGFloat = 14
    public static let composerTextVerticalPaddingExpanded: CGFloat = 12
    public static let composerActionButtonSize: CGFloat = 30
    public static let composerPlusButtonSize: CGFloat = 28
    public static let composerModelControlMaxWidth: CGFloat = 132
    public static let composerModelControlMaxWidthAccessibility: CGFloat = 156
    public static let composerReasoningControlWidth: CGFloat = 104
    public static let composerReasoningControlWidthAccessibility: CGFloat = 126
    public static let composerBottomAccessorySpacing: CGFloat = 7
    public static let composerGradientTopPadding: CGFloat = 30
    public static let composerSecondaryBarSpacing: CGFloat = 8
    public static let composerSecondaryBarVerticalPadding: CGFloat = 8
    public static let composerSecondaryBarHorizontalPadding: CGFloat = 14
    public static let composerSecondaryBarVerticalPaddingAccessibility: CGFloat = 10
    public static let composerSecondaryBarHorizontalPaddingAccessibility: CGFloat = 16
    public static let composerQuickActionSpacing: CGFloat = 8
    public static let composerAttachmentStripHeight: CGFloat = 32
    public static let pendingPromptHorizontalPadding: CGFloat = 16
    public static let pendingPromptBottomSpacing: CGFloat = 10
    public static let pendingPromptCornerRadius: CGFloat = 18
    public static let sessionListHorizontalPadding: CGFloat = 24
    public static let sessionListTopPadding: CGFloat = 28
    public static let sessionListTopChromeBottomPadding: CGFloat = 18
    public static let sessionListUtilityTopPadding: CGFloat = 10
    public static let sessionListBottomSpacerHeight: CGFloat = 104
    public static let sessionListFloatingButtonTrailing: CGFloat = 24
    public static let sessionListFloatingButtonBottom: CGFloat = 22
    public static let sessionListFloatingButtonHeight: CGFloat = 58
    public static let sessionRowHorizontalPadding: CGFloat = 12
    public static let sessionRowVerticalPadding: CGFloat = 8
    public static let sessionRowHorizontalSpacing: CGFloat = 10
    public static let sessionRowContentSpacing: CGFloat = 4
    public static let sessionRowContentSpacingAccessibility: CGFloat = 6
    public static let sessionRowMinimumHeight: CGFloat = 46
    public static let sessionRowSupplementalMinimumHeight: CGFloat = 54
    public static let sessionRowTitleDateSpacing: CGFloat = 8
    public static let sessionRowTitlePinSpacing: CGFloat = 6
    public static let sessionRowMetadataSpacing: CGFloat = 7
    public static let sessionRowStateBadgeSpacing: CGFloat = 5
    public static let sessionRowStateBadgeHorizontalPadding: CGFloat = 5
    public static let sessionRowStateBadgeVerticalPadding: CGFloat = 2
    public static let sessionRowStreamingIndicatorSize: CGFloat = 9
    public static let sessionRowStreamingIndicatorTopPadding: CGFloat = 7
    public static let sessionRowStreamingIndicatorTopPaddingAccessibility: CGFloat = 8
    public static let sessionListFloatingButtonShadowRadius: CGFloat = 18
    public static let sessionListFloatingButtonPressedShadowRadius: CGFloat = 8
    public static let sessionListFloatingButtonShadowYOffset: CGFloat = 8
    public static let sessionListFloatingButtonPressedShadowYOffset: CGFloat = 3
    public static let sessionListFloatingButtonShadowOpacity: Double = 0.18
    public static let sessionListFloatingButtonPressedShadowOpacity: Double = 0.10
    public static let pressedScale: CGFloat = 0.975
    public static let pressedOpacity: Double = 0.96
}

public struct HermexMeasuredBottomInset<Content: View>: View {
    @Binding private var measuredHeight: CGFloat
    private let extraInset: CGFloat
    private let content: Content

    public init(
        measuredHeight: Binding<CGFloat>,
        extraInset: CGFloat = 28,
        @ViewBuilder content: () -> Content
    ) {
        self._measuredHeight = measuredHeight
        self.extraInset = extraInset
        self.content = content()
    }

    public var body: some View {
        content
            .background(
                GeometryReader { proxy in
                    Color.clear
                        .onAppear {
                            measuredHeight = proxy.size.height + extraInset
                        }
                        .onChange(of: proxy.size.height) { _, newValue in
                            measuredHeight = newValue + extraInset
                        }
                }
            )
    }
}
