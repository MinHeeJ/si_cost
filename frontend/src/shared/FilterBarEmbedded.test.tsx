import {fireEvent,render,screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {expect,it,vi} from "vitest";
import {FilterBar} from "./FilterBar";
it("모달 form 내부 검색은 상위 저장을 제출하지 않고 클릭/Enter/초기화를 처리한다",async()=>{
 const save=vi.fn(),search=vi.fn(),reset=vi.fn();
 render(<form onSubmit={e=>{e.preventDefault();save();}}><FilterBar embedded onSubmit={search} onReset={reset}><label>검색<input/></label></FilterBar><button type="submit">저장</button></form>);
 expect(document.querySelectorAll("form")).toHaveLength(1);
 await userEvent.click(screen.getByRole("button",{name:"조회"}));expect(search).toHaveBeenCalledTimes(1);
 fireEvent.keyDown(screen.getByLabelText("검색"),{key:"Enter"});expect(search).toHaveBeenCalledTimes(2);expect(save).not.toHaveBeenCalled();
 await userEvent.click(screen.getByRole("button",{name:"초기화"}));expect(reset).toHaveBeenCalledOnce();
 await userEvent.click(screen.getByRole("button",{name:"저장"}));expect(save).toHaveBeenCalledOnce();
});
