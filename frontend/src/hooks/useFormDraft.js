import { useState, useEffect } from "react";

// 입력 임시 저장 훅 (localStorage hfm_draft_<key>, 화면 재진입 시 복원)
export function useFormDraft(draftKey, initialValues = {}) {
  const [values, setValues] = useState(() => {
    try {
      const saved = localStorage.getItem(`hfm_draft_${draftKey}`);
      return saved ? JSON.parse(saved) : initialValues;
    } catch {
      return initialValues;
    }
  });

  useEffect(() => {
    localStorage.setItem(`hfm_draft_${draftKey}`, JSON.stringify(values));
  }, [draftKey, values]);

  const clearDraft = () => {
    localStorage.removeItem(`hfm_draft_${draftKey}`);
    setValues(initialValues);
  };

  return { values, setValues, clearDraft };
}
