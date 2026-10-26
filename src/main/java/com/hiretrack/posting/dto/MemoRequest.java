package com.hiretrack.posting.dto;

import jakarta.validation.constraints.Size;

public record MemoRequest(@Size(max = 10000) String memo) {
}
