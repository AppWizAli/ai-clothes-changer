package com.ai.tryonclothes.photo.generator.app.domain

import com.ai.tryonclothes.photo.generator.app.core.AppConfig
import com.ai.tryonclothes.photo.generator.app.core.ImageProblem
import com.ai.tryonclothes.photo.generator.app.domain.validation.ImageValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageValidatorTest {
    private fun validate(mime: String? = "image/jpeg", size: Long = 1_000_000, w: Int = 1200, h: Int = 1600) =
        ImageValidator.validate(mime, size, w, h)

    @Test fun acceptsTypicalPhoto() = assertNull(validate())
    @Test fun acceptsPngAndWebp() { assertNull(validate("image/png")); assertNull(validate("image/webp")) }
    @Test fun rejectsUnsupportedType() = assertEquals(ImageProblem.UNSUPPORTED_TYPE, validate(mime = "image/gif"))
    @Test fun rejectsUnknownType() = assertEquals(ImageProblem.UNSUPPORTED_TYPE, validate(mime = null))
    @Test fun rejectsOversizedFile() = assertEquals(ImageProblem.FILE_TOO_LARGE, validate(size = AppConfig.MAX_SOURCE_BYTES + 1))
    @Test fun acceptsFileAtExactLimit() = assertNull(validate(size = AppConfig.MAX_SOURCE_BYTES))
    @Test fun rejectsTinyImage() = assertEquals(ImageProblem.TOO_SMALL, validate(w = 200, h = 4000))
    @Test fun rejectsHugeDimensions() = assertEquals(ImageProblem.TOO_LARGE_DIMENSIONS, validate(w = 13_000, h = 3000))
    @Test fun rejectsZeroSizeAsUnreadable() = assertEquals(ImageProblem.UNREADABLE, validate(w = 0, h = 0))
}
